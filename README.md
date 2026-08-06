# JSON 接口自动解析建表入库系统

基于 Spring Boot 3.2、MyBatis-Plus、Quartz、OkHttp、Jackson、Vue 3 + Element Plus 的可运行实现。读取任意 JSON 接口，自动识别根数组、扁平化嵌套对象、创建主表与数组子表，按业务唯一 Key 增量新增、更新和哈希去重。

## 功能特性

- GET/POST 接口，Header、Query、JSON Body 与动态参数（`{{now}}`/`{{date}}`/`{{last_success_time}}`/`{{env.XXX}}`）
- Basic、表单登录、固定 Token、可刷新 Token 认证，敏感配置 AES-256-GCM 加密
- JSON 根路径自动识别、嵌套对象扁平化、对象数组子表化、标量数组 JSON 化
- 目标库适配：MySQL / PostgreSQL / SQL Server / Oracle
- 首次全量、唯一 Key 增量、记录哈希去重、无主键安全追加、人工全量重刷
- 仅新增字段的自动结构演进，结构版本与字段映射留档
- Quartz 调度（JDBC JobStore 持久化、集群就绪、失败重试、同任务并发互斥）
- 执行日志、失败告警（Webhook 推送）、审计日志（SHA-256 哈希链防篡改）、数据预览、CSV 导出
- 管理员/配置员/执行员/查看员四级权限，登录失败锁定（10 次/30 分钟）
- **自动中文注释**：接口返回中文 JSON 字段时列名保留中文；英文字段经内置词典 + 自定义映射自动生成中文注释（完全离线）
- Vue 3 + Element Plus 响应式管理后台

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17, Spring Boot 3.2.12, MyBatis-Plus 3.5.7, Quartz, OkHttp, Jackson, Spring Security |
| 前端 | Vue 3, Element Plus, Vite |
| 数据库 | MySQL 8.x（元数据 + 目标数据），支持 PG/SQLServer/Oracle 目标库 |

## 快速开始

### 环境变量（全部必填，不内置任何凭据）

| 变量 | 说明 | 示例 |
|---|---|---|
| `META_DB_URL` | 元数据库 JDBC URL | `jdbc:mysql://127.0.0.1:3306/json_ingestion?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false` |
| `META_DB_USER` | 元数据库账号 | `json_ingestion` |
| `META_DB_PASSWORD` | 元数据库密码 | 部署时自行设置强密码 |
| `TARGET_DB_HOST/PORT/NAME/USER/PASSWORD` | 首次种子目标库连接 | `127.0.0.1 / 3306 / json_ingestion_target / ...` |
| `INGESTION_SECRET_FILE` | AES 主密钥文件路径 | `/opt/json-ingestion-system/data/.secret`（启动时自动生成） |
| `INGESTION_ALLOW_PRIVATE_URLS` | 是否允许访问内网接口地址（SSRF 防护开关） | 内网业务接口 `true`，全公网可 `false` |
| `INGESTION_PORT` | HTTP 端口 | `3100` |

### 构建

```bash
# 后端（仓库不含前端构建产物, 首次构建请先构建前端或直接使用 build-system.cmd）
mvn -f backend/pom.xml package -DskipTests
# 或完整构建（前端改动时）
./build-system.cmd   # Windows
```

### 启动

```bash
# Linux
java -jar backend/target/json-ingestion-system-1.0.0.jar
# 生产推荐 systemd: 见 deploy/json-ingestion.service

# Windows
start-system.cmd
```

首次启动自动：初始化 4 个演示账号、创建"演示订单接口"任务、建 QRTZ_ 调度表、生成 data/.secret。

### 初始账号（**首次登录必须修改**）

| 角色 | 账号 | 说明 |
|---|---|---|
| 管理员 | `admin` | 登录后立即 POST /api/password/change 修改 |
| 配置员 | `config` | 管理员可 POST /api/password/reset 重置 |
| 执行员 | `runner` | 同上 |
| 查看员 | `viewer` | 同上 |

## 自动中文注释

接口返回 JSON 字段名为中文时（如 `身份证号码`、`电话`），建表时列名保留中文并自动作为注释；英文字段名按 自定义映射(`field_comment_map`) > 内置词典(200+ 字段) 生成中文注释。生产环境无需任何外部 AI 服务，完全离线。

## 生产部署

见 [deploy/PRODUCTION-DEPLOYMENT.md](deploy/PRODUCTION-DEPLOYMENT.md)：
- systemd 单元 + Nginx HTTPS 反代
- 每日备份脚本（元库 + 目标库 + .secret 三件套）
- 审计哈希链校验与运维
- 上线安全清单

## 安全设计

- 密码 BCrypt-11；敏感配置 AES-256-GCM（密钥文件 ACL 收紧）
- 登录失败 10 次锁定 30 分钟（不信任 X-Forwarded-For，防伪造绕过）
- SSRF 防护：仅 http/https + 内网/回环/云元数据地址拦截（可用环境变量放开）
- 审计日志 SHA-256 哈希链：每条记录哈希含上一条哈希 + 全部字段，POST /api/audit/verify 全链校验
- 错误信息脱敏；Token 固定 8 小时过期；服务默认仅绑定 127.0.0.1

## 源码结构

```text
backend/     Spring Boot、MyBatis-Plus、Quartz、OkHttp、Jackson
frontend/    Vue 3、Element Plus、Vite
deploy/      systemd、Nginx、部署文档
backup.cmd/.sh  备份脚本
```
