# 生产部署指南

## 架构总览

```
[浏览器] --HTTPS--> [Nginx :443] --http--> [Spring Boot :3100 (127.0.0.1)]
                                              |-- 元数据库 json_ingestion (MySQL :3306)
                                              |-- 目标库   json_ingestion_target
                                              |-- data/.secret (AES 主密钥, ACL 已收紧)
                                              `-- Quartz JDBC JobStore (QRTZ_ 表, 在元数据库)
```

## 1. 进程守护

### Windows(本机/Windows 服务器)
- 已注册计划任务 `JsonIngest3306`(开机自启, onstart), 启动脚本 `start-nohup-3306.cmd`
- 手动启停: `schtasks /run /tn JsonIngest3306` / `schtasks /end /tn JsonIngest3306`
- 或用 `start-system-3306.cmd`(交互式启动, 会打开浏览器)

### Linux
- 单元文件: `deploy/json-ingestion.service`, 部署:
```bash
sudo cp deploy/json-ingestion.service /etc/systemd/system/
sudo useradd -r -s /sbin/nologin ingestion        # 专用低权限账号
sudo mkdir -p /opt/json-ingestion-system
sudo cp backend/target/json-ingestion-system-1.0.0.jar /opt/json-ingestion-system/
sudo chown -R ingestion:ingestion /opt/json-ingestion-system
sudo systemctl daemon-reload
sudo systemctl enable --now json-ingestion
```
- 常用命令: `systemctl status json-ingestion` / `journalctl -u json-ingestion -f`

## 2. 反向代理 HTTPS

- 配置示例: `deploy/nginx-json-ingestion.conf`(HTTPS + HTTP跳转 + 导出超时)
- 证书: certbot 免费证书或企业证书
- **注意**: 应用出于防爆破考虑不信任 X-Forwarded-For, 反代后审计日志来源 IP 显示 127.0.0.1 属正常设计

## 3. 备份(最重要, 无备份=无数据)

- Windows: 双击/计划任务运行 `backup.cmd`(建议每日 02:00)
- Linux: `crontab -e` 添加 `0 2 * * * /opt/json-ingestion-system/backup.sh`
- 备份内容: 元数据库 + 目标库 + `data/.secret`(三者必须一起备份/一起恢复, 缺 .secret 无法解密密码)
- 保留最近 14 份, 自动清理; **建议备份目录再同步到异地**(NAS/对象存储)
- 恢复演练: 备份的 sql 可用 `mysql < backup.sql` 导入, secret.txt 放回 data/.secret

## 4. 环境变量(生产覆盖默认值)

| 变量 | 默认 | 说明 |
|---|---|---|
| META_DB_URL | 3306 json_ingestion | 元数据库 JDBC(生产改为独立 MySQL 实例) |
| META_DB_USER / META_DB_PASSWORD | json_ingestion / (已换) | 生产部署前再换一批新密码 |
| TARGET_DB_* | 127.0.0.1:3306 json_ingestion_target | 种子目标库连接 |
| INGESTION_SECRET_FILE | data/.secret | AES 主密钥路径(纳入备份+ACL) |
| INGESTION_ALLOW_PRIVATE_URLS | true | 内网业务接口需要 true; 全公网接口可 false(SSRF 防护更强) |

## 5. 调度持久化(已启用)

- Quartz 已切换 **JDBC JobStore**: 触发器持久化到元数据库 QRTZ_ 表, 重启不丢调度, 已配置集群模式(isClustered=true, 单实例同样适用, 未来加实例直接支持)
- 多实例上线前还需: Redis 会话共享 + 分布式锁(任务互斥/登录锁定/审计链写锁) —— 见安全审查报告

## 6. 上线前安全清单

- [ ] 更换 4 个应用账号密码(admin/config/runner/viewer)和管理员重置接口验证
- [ ] 更换 MySQL json_ingestion 账号密码并同步更新脚本/数据源
- [ ] `data/.secret` ACL: 仅应用账号可读写(SYSTEM + 应用用户)
- [ ] 审计链校验: `POST /api/audit/verify` 应返回 valid=true(定期抽查)
- [ ] 配置告警 Webhook: 系统设置 → alert_webhook_url(钉钉/企微/飞书)
- [ ] 维护 field_comment_map 自定义字段注释(业务字段中文映射)
- [ ] 生产确认 `comment_translate_enabled=false`(无 Ollama)
- [ ] 日志目录日志轮转(logback 已按天滚动+压缩)
- [ ] 防火墙: 只开放 443(HTTPS), 3306/3100 不对公网

## 7. 升级流程

```bash
# 1. 备份(backup.cmd / backup.sh)
# 2. 停服务
schtasks /end /tn JsonIngest3306    # Windows
sudo systemctl stop json-ingestion  # Linux
# 3. 替换 jar(保留 logs/ data/ 目录)
# 4. 启动 + 健康检查
curl http://127.0.0.1:3100/api/health
# 5. 审计链校验 + 抽查一个任务执行
```

## 8. 常见问题

| 现象 | 处理 |
|---|---|
| 启动失败 Connection refused | 检查 META_DB_URL 端口/账号(环境变量未生效时会回落到默认值) |
| 审计链校验失败 | 说明日志被改动过; 用 /api/audit/list 定位 id, 排查来源 |
| 任务不触发 | system_setting.scheduler_enabled=true; 任务 enabled=true; 看 QRTZ_TRIGGERS 状态 |
| 告警没推送 | 检查 alert_webhook_url 配置与网络(推送失败只在日志留 warn) |
## 9. 审计哈希链运维

- 审计链格式: row_hash = SHA256(prev_hash | 用户 | 动作 | 模块 | 详情 | IP | 时间[秒])
- 时间统一秒精度(写入时 withNano(0)),与 MySQL DATETIME 存储完全一致,避免四舍五入差异
- 校验: POST /api/audit/verify; 查询: GET /api/audit/list
- **若校验失败**: 定位断裂 id → 排查是否被篡改 → 确认是历史格式问题(非篡改)时,可清空该 id 及之后记录的 prev_hash/row_hash 并重启(自动补链),生产环境该操作需双人确认并记录
- 多实例部署时,审计写入需加分布式锁(Redis/DB),否则链可能分叉