# JSON 接口自动解析建表入库系统

基于 Spring Boot 3.2、MyBatis-Plus、Quartz、OkHttp、Jackson、Vue 3 + Element Plus 的可运行实现。读取任意 JSON 接口，自动识别根数组、扁平化嵌套对象、创建主表与数组子表，按业务唯一 Key 增量新增、更新和哈希去重。

## 功能特性

- GET/POST 接口，Header、Query、JSON Body 与动态参数（`{{now}}`/`{{date}}`/`{{last_success_time}}`/`{{env.XXX}}`）
- Basic、表单登录、固定 Token、可刷新 Token 认证，敏感配置 AES-256-GCM 加密
- JSON 根路径自动识别、嵌套对象扁平化、对象数组子表化、标量数组 JSON 化
- 目标库适配：MySQL / PostgreSQL / SQL Server / Oracle
- 首次全量、唯一 Key 增量、记录哈希去重、无主键安全追加、人工全量重刷（重刷按批次号清理旧行，不再"先清空表再写入"）
- 自动结构演进：新字段自动补列，字段变长/变宽时在同族类型内安全加宽（`VARCHAR(255)`→`VARCHAR(500)`、`INT`→`BIGINT`），结构版本与字段映射留档
- Quartz 调度（JDBC JobStore 持久化、集群就绪、失败重试、同任务并发互斥）
- 执行日志、失败告警（Webhook 推送）、审计日志（HMAC-SHA256 哈希链防篡改）、数据预览、CSV 导出
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

### 环境变量（不内置任何凭据，样例见 `deploy/json-ingestion.env.example`）

| 变量 | 默认 | 说明 |
|---|---|---|
| `META_DB_URL` | `jdbc:mysql://127.0.0.1:3306/json_ingestion?...` | 元数据库 JDBC URL，URL 参数受方言白名单校验 |
| `META_DB_USER` | `json_ingestion` | 元数据库账号 |
| `META_DB_PASSWORD` | 空 | 元数据库密码，**部署时必须设置强密码** |
| `TARGET_DB_HOST/PORT/NAME/USER/PASSWORD` | `127.0.0.1 / 3306 / json_ingestion_target` | 首次启动写入的种子目标库连接 |
| `INGESTION_SECRET_FILE` | `data/.secret` | AES 主密钥文件，启动时自动生成，**必须纳入备份与 ACL 收紧** |
| `INGESTION_INIT_ADMIN_PASSWORD`<br>`INGESTION_INIT_CONFIG_PASSWORD`<br>`INGESTION_INIT_RUNNER_PASSWORD`<br>`INGESTION_INIT_VIEWER_PASSWORD` | 空 | 4 个初始账号密码；未设置时启动生成一次性随机密码，只在日志里输出一次 |
| `INGESTION_PORT` | `3100` | HTTP 端口 |
| `SERVER_ADDRESS` | `127.0.0.1` | 监听地址，直接对外暴露时才改 `0.0.0.0` |
| `INGESTION_TRUST_PROXY` | `true` | 是否采信回环反代传来的 `X-Forwarded-For`；应用直接对外时必须设 `false` |
| `INGESTION_ALLOW_PRIVATE_URLS` | `false` | 是否允许任务访问内网/回环地址（SSRF 防护开关）；内置演示接口需要 `true` |
| `INGESTION_DEMO_API` | `true` | 是否开放免鉴权的内置演示接口，生产环境设 `false` |

### 构建

```bash
# 前端产物直接输出到 backend/src/main/resources/static, 必须先构建前端
cd frontend && npm ci && npm run build && cd ..
# 后端（含单元测试）
mvn -f backend/pom.xml verify

# Windows 一键构建（前端 + 后端 + 测试）
build-system.cmd
```

### 启动

```bash
# Linux
java -jar backend/target/json-ingestion-system-1.0.0.jar
# 生产推荐 systemd: 见 deploy/json-ingestion.service

# Windows
start-system.cmd
```

首次启动自动：初始化 4 个账号（密码见下节）、创建"演示订单接口"任务、建 QRTZ_ 调度表、生成 `data/.secret`、执行幂等结构迁移。

### 初始账号（**首次登录强制改密**）

| 角色 | 账号 | 初始密码来源 |
|---|---|---|
| 管理员 | `admin` | `INGESTION_INIT_ADMIN_PASSWORD`，未设置则启动时生成一次性随机密码并打印在日志中 |
| 配置员 | `config` | `INGESTION_INIT_CONFIG_PASSWORD`，同上 |
| 执行员 | `runner` | `INGESTION_INIT_RUNNER_PASSWORD`，同上 |
| 查看员 | `viewer` | `INGESTION_INIT_VIEWER_PASSWORD`，同上 |

代码、配置文件与 SQL 里都不内置任何默认口令，登录页也没有一键填充。4 个账号首次登录都会被强制改密（`must_change_password`），改密成功后该用户已有 Token 立即失效；忘记密码由管理员调用 `POST /api/password/reset` 重置。

## 自动中文注释

接口返回 JSON 字段名为中文时（如 `身份证号码`、`电话`），建表时列名保留中文并自动作为注释；英文字段名按 自定义映射(`field_comment_map`) > 内置词典(200+ 字段) 生成中文注释。生产环境无需任何外部 AI 服务，完全离线。

## 生产部署

见 [deploy/PRODUCTION-DEPLOYMENT.md](deploy/PRODUCTION-DEPLOYMENT.md)：
- systemd 单元 + Nginx HTTPS 反代
- 每日备份脚本（元库 + 目标库 + .secret 三件套）
- 审计哈希链校验与运维
- 上线安全清单

## 安全设计

- 密码 BCrypt-11；敏感配置 AES-256-GCM，审计链使用从主密钥派生的独立子密钥
- 接口鉴权默认拒绝（`anyRequest().authenticated()`），只放行登录、健康检查与（可关闭的）演示接口
- 登录失败 10 次锁定 30 分钟；只有回环反代传来的 `X-Forwarded-For` 才被采信，客户端伪造的该头一律忽略
- SSRF 防护：仅 http/https + 内网/回环/云元数据地址拦截（`INGESTION_ALLOW_PRIVATE_URLS` 放开）
- JDBC 连接串按方言做参数白名单校验，`autoDeserialize`/`socketFactory`/`allowLoadLocalInfile` 等可导致 RCE 或读本地文件的参数一律拒绝
- 审计日志 HMAC-SHA256 哈希链（长度前缀规范化 + 链尾行锁）：`POST /api/audit/verify` 全链校验，`POST /api/audit/rechain` 仅管理员可触发且动作本身留痕告警
- 错误信息脱敏（对外只回事件号，堆栈仅落库）；执行记录中的 Header/密钥字段回显时打码，回写时自动还原
- Token 8 小时过期、改密即吊销；服务默认仅绑定 127.0.0.1

## 源码结构

```text
backend/     Spring Boot、MyBatis-Plus、Quartz、OkHttp、Jackson（src/test 为单元测试）
frontend/    Vue 3、Element Plus、Vite
deploy/      systemd、Nginx、部署文档、环境变量样例
.github/     CI：前端构建 + mvn verify
backup.cmd/.sh  备份脚本
```
