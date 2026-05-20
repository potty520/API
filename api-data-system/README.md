# API数据解析与自动入库系统

这是一个完整的 API 数据解析与自动入库系统，采用前后端分离架构。

## 技术栈

- **后端**: Java 1.8 + Spring Boot 2.7
- **前端**: Vue 3 + Element Plus
- **数据库**: MySQL 8.0
- **ORM**: MyBatis-Plus
- **任务调度**: Spring Scheduler

## 项目结构

```
api-data-system/
├── backend/                 # Spring Boot 后端项目
│   ├── src/main/java/com/example/apisystem/
│   │   ├── controller/      # 控制器层
│   │   ├── service/         # 服务层
│   │   ├── entity/          # 实体类
│   │   ├── repository/      # 数据访问层
│   │   ├── dto/             # 数据传输对象
│   │   ├── config/          # 配置类
│   │   └── scheduler/       # 任务调度器
│   ├── src/main/resources/
│   │   └── application.yml  # 配置文件
│   └── pom.xml              # Maven 依赖
│
├── frontend/                 # Vue3 前端项目
│   ├── src/
│   │   ├── views/          # 页面组件
│   │   ├── api/            # API 调用
│   │   ├── router/         # 路由配置
│   │   ├── utils/          # 工具类
│   │   └── components/     # 公共组件
│   ├── package.json
│   └── vite.config.js
│
└── scripts/                 # 数据库脚本
    └── init.sql            # 数据库初始化脚本
```

## 功能特性

### 二期功能（已实现）

#### 1. 仪表盘
- 任务执行成功率统计
- 今日数据拉取总量
- 任务状态分布饼图
- 最近执行记录展示
- 今日执行统计（成功/失败/新增）

#### 2. Cron 调度
- 可视化 Cron 表达式配置
- Cron 表达式验证和预览
- 常用 Cron 示例快速选择
- 任务启停控制
- 手动执行任务
- 下次执行时间预览

### 一期功能（基础模块）

#### 3. Token 管理
- Token 配置（固定/动态）
- Token 状态监控
- 动态 Token 自动获取

#### 4. 接口管理
- API 接口配置
- 请求参数配置
- 数据提取路径配置

#### 5. 映射配置
- 字段映射管理
- JSONPath 字段提取
- 业务唯一键配置
- 目标表定义

#### 6. 数据与日志
- 任务执行日志查询
- 详细日志查看
- 错误信息追踪

## 快速开始

### 环境要求

- JDK 1.8+
- Node.js 16+
- MySQL 8.0+

### 1. 初始化数据库

```bash
mysql -u root -p < scripts/init.sql
```

### 2. 配置后端

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/api_data_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
```

### 3. 启动后端

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

后端将在 http://localhost:8080 启动

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端将在 http://localhost:3000 启动

## API 接口

### 仪表盘接口

- `GET /api/dashboard/stats` - 获取仪表盘统计数据

### 任务调度接口

- `GET /api/task/list` - 获取任务列表
- `POST /api/task/save` - 保存任务
- `DELETE /api/task/delete/{id}` - 删除任务
- `POST /api/task/toggle/{id}` - 切换任务状态
- `POST /api/task/execute/{id}` - 执行任务
- `GET /api/task/validate-cron` - 验证 Cron 表达式
- `GET /api/task/cron-next-time` - 获取下次执行时间

### Token 管理接口

- `GET /api/token/list` - 获取 Token 列表
- `POST /api/token/save` - 保存 Token 配置
- `DELETE /api/token/delete/{id}` - 删除 Token

### API 配置接口

- `GET /api/api-config/list` - 获取 API 配置列表
- `POST /api/api-config/save` - 保存 API 配置

### 字段映射接口

- `GET /api/field-mapping/list/{apiConfigId}` - 获取字段映射列表
- `POST /api/field-mapping/save` - 保存字段映射

### 日志接口

- `GET /api/log/list` - 获取日志列表
- `GET /api/log/detail/{id}` - 获取日志详情

## Cron 表达式示例

- `0 * * * *` - 每分钟
- `0 */5 * * *` - 每 5 分钟
- `0 0 * * *` - 每小时
- `0 0 8 * *` - 每天 8 点
- `0 0 0 * *` - 每天午夜
- `0 0 9 * * 1` - 每周一 9 点

## 数据库表说明

1. **token_config** - Token 配置表
2. **api_config** - API 接口配置表
3. **field_mapping** - 字段映射配置表
4. **scheduled_task** - 定时任务配置表
5. **task_execution_log** - 任务执行日志表

## 注意事项

1. 首次使用请先修改数据库连接配置
2. 启动任务前请确保字段映射配置正确
3. 建议在生产环境使用独立的数据库用户
4. 定时任务依赖系统时钟，请确保服务器时间准确

## License

MIT License
