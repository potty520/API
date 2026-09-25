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
- 所有 .cmd 均为 UTF-8 + `chcp 65001`, 密码只从环境变量读取: 先 `setx META_DB_PASSWORD "强密码"` 再重开命令行窗口
- `start-system.cmd` 使用项目自带的专用 MySQL(3307, 数据目录 `mysql-data`), 与本机 3306 实例互不影响

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
- **X-Forwarded-For**: Nginx 必须用 `proxy_set_header X-Forwarded-For $remote_addr`(覆盖而非追加); 应用只在 TCP 对端是回环地址时才采信该头, 因此反代后审计能记录真实来源, 而客户端伪造的头无法绕过登录锁定
- 应用不经反代直接对外时, 必须设 `INGESTION_TRUST_PROXY=false`, 否则来源 IP 可被伪造

## 3. 备份(最重要, 无备份=无数据)

- Windows: 双击/计划任务运行 `backup.cmd`(建议每日 02:00)
- Linux: `crontab -e` 添加 `0 2 * * * /opt/json-ingestion-system/backup.sh`
- 备份内容: 元数据库 + 目标库 + `data/.secret`(三者必须一起备份/一起恢复, 缺 .secret 无法解密密码)
- 保留最近 14 份, 自动清理; **建议备份目录再同步到异地**(NAS/对象存储)
- 恢复演练: 备份的 sql 可用 `mysql < backup.sql` 导入, secret.txt 放回 data/.secret

## 4. 环境变量(生产覆盖默认值)

完整样例见 `deploy/json-ingestion.env.example`(systemd 通过 `EnvironmentFile` 加载)。

| 变量 | 默认 | 说明 |
|---|---|---|
| `META_DB_URL` | 127.0.0.1:3306 json_ingestion | 元数据库 JDBC(生产改为独立 MySQL 实例); URL 参数受方言白名单校验 |
| `META_DB_USER` / `META_DB_PASSWORD` | json_ingestion / 空 | 生产部署前设置强密码, 不要写进脚本或仓库 |
| `TARGET_DB_*` | 127.0.0.1:3306 json_ingestion_target | 首次启动写入的种子目标库连接 |
| `INGESTION_SECRET_FILE` | data/.secret | AES 主密钥路径(纳入备份 + ACL 收紧), 同时用于派生审计链子密钥 |
| `INGESTION_INIT_ADMIN_PASSWORD` 等 4 个 | 空 | 初始账号密码; 未设置时启动生成一次性随机密码, 只在日志输出一次 |
| `INGESTION_PORT` | 3100 | HTTP 端口 |
| `SERVER_ADDRESS` | 127.0.0.1 | 监听地址, 反代部署保持默认 |
| `INGESTION_TRUST_PROXY` | true | 是否采信回环反代的 `X-Forwarded-For`; 直接对外时必须 false |
| `INGESTION_ALLOW_PRIVATE_URLS` | false | 内网业务接口需要 true; 全公网接口保持 false(SSRF 防护更强) |
| `INGESTION_DEMO_API` | true | 免鉴权的内置演示接口开关, 生产必须 false |

## 5. 调度持久化(已启用)

- Quartz 已切换 **JDBC JobStore**: 触发器持久化到元数据库 QRTZ_ 表, 重启不丢调度, 已配置集群模式(isClustered=true, 单实例同样适用, 未来加实例直接支持)
- 全局调度开关 `system_setting.scheduler_enabled` 修改后立即 pauseAll/resumeAll 并重新对账, 不需要重启
- 多实例上线前还需: 会话共享(Token 目前保存在单实例内存中, 需换 Redis) + 任务互斥/登录锁定的分布式锁; 审计链写入已用 `SELECT ... FOR UPDATE` 锁住链尾, 共享同一元数据库即可避免分叉

## 6. 上线前安全清单

- [ ] 4 个初始账号(admin/config/runner/viewer)首次登录后立即改密(系统会强制), 并验证管理员重置接口
- [ ] 更换 MySQL json_ingestion 账号密码并同步更新脚本/数据源
- [ ] `data/.secret` ACL: 仅应用账号可读写(SYSTEM + 应用用户)
- [ ] 审计链校验: `POST /api/audit/verify` 应返回 `valid=true`, 且 `checked` 与 `GET /api/audit/list` 总数一致(定期抽查)
- [ ] `INGESTION_DEMO_API=false` 关闭免鉴权演示接口
- [ ] 反代部署保持 `SERVER_ADDRESS=127.0.0.1`; 若直接对外则设 `INGESTION_TRUST_PROXY=false`
- [ ] `INGESTION_ALLOW_PRIVATE_URLS` 按真实接口来源收紧; 保存数据源时确认 JDBC 参数都在白名单内
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
| 审计链校验失败 | 用 `GET /api/audit/list` 定位断裂 id 并排查来源; 确认不是篡改后, 再由管理员 `POST /api/audit/rechain` 重建 |
| 任务不触发 | system_setting.scheduler_enabled=true; 任务 enabled=true; 看 QRTZ_TRIGGERS 状态 |
| 告警没推送 | 检查 alert_webhook_url 配置与网络(推送失败只在日志留 warn) |
| 登录被要求先改密 | 初始账号 `must_change_password=1`, 属预期行为; 改密成功后旧 Token 会被吊销 |
| 保存数据源报"JDBC 参数不在允许清单内" | 该参数不在白名单(部分参数可直接导致 RCE 或读取本地文件); 请改用主机/端口/库名字段, 确需使用时由管理员评估后加入 `JdbcUrlGuard` |
| 人工全量重刷后行数变少 | 重刷会按 `_batch_id` 清理本任务上一批遗留的行(条数见执行详情 `pruned`); 其它任务或人工写入的数据不受影响 |
| 某列被自动加宽 | 字段变长/变宽属正常演进, 变更记录见执行日志的 `WIDEN_COLUMNS`; 单列加宽失败只告警不中断同步 |
## 9. 审计哈希链运维

- 当前算法(v2): `row_hash = HMAC-SHA256(由 data/.secret 派生的审计子密钥, 长度前缀规范化的 prev_hash + 用户 + 动作 + 模块 + 详情 + IP + 时间[秒])`
  - 长度前缀编码保证不同字段组合不会规范化成同一个串(避免拼接歧义伪造)
  - 没有主密钥文件就算不出合法 row_hash, 只有数据库写权限无法伪造整条链
- 历史 v1 记录(`chain_version < 2`)仍按旧算法 `SHA256(prev_hash|用户|动作|模块|详情|IP|时间)` 校验, 不会被误报为篡改
- 时间统一秒精度(写入时 `withNano(0)`), 与 MySQL DATETIME 存储一致, 避免四舍五入差异
- 校验: `POST /api/audit/verify`(管理员), 返回 `valid` / `error` / `checked`; 查询: `GET /api/audit/list`
- 并发: 写入前用 `SELECT ... FOR UPDATE` 锁住链尾, 多实例共享同一元数据库时不会分叉
- **若校验失败**: 先定位断裂 id 与来源(是否有人直接改过库、是否跨库迁移过) → 确认属历史格式升级或误操作后, 由管理员调用 `POST /api/audit/rechain` 重建
  - 重链会抹掉篡改痕迹, 因此**不会在启动时自动执行**, 只能显式调用; 该动作本身会写入审计并触发一条高危告警
  - 生产环境执行前请双人确认、先做备份, 并在变更单里记录原因与操作人
