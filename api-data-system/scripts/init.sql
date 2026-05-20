-- API 数据解析与自动入库系统 - 数据库初始化脚本
-- 数据库: api_data_system
-- 版本: 1.0.0

-- 创建数据库
CREATE DATABASE IF NOT EXISTS api_data_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE api_data_system;

-- Token 配置表
CREATE TABLE IF NOT EXISTS token_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT 'Token配置名称',
    token_type VARCHAR(20) NOT NULL COMMENT 'Token类型: FIXED-固定Token, DYNAMIC-动态获取',
    fixed_token VARCHAR(500) COMMENT '固定Token值',
    token_url VARCHAR(500) COMMENT 'Token获取接口URL',
    token_method VARCHAR(10) DEFAULT 'POST' COMMENT '请求方式: GET, POST',
    token_params TEXT COMMENT 'Token获取参数(JSON)',
    token_headers TEXT COMMENT 'Token请求头(JSON)',
    token_extract_path VARCHAR(200) COMMENT 'Token提取路径(JSONPath)',
    expires_in_path VARCHAR(200) COMMENT '过期时间提取路径(JSONPath)',
    default_expires_seconds INT DEFAULT 7200 COMMENT '默认过期时间(秒)',
    refresh_before_minutes INT DEFAULT 2 COMMENT '提前刷新时间(分钟)',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-有效, INACTIVE-无效, EXPIRED-已过期',
    current_token VARCHAR(500) COMMENT '当前Token值',
    token_expire_time DATETIME COMMENT 'Token过期时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注',
    INDEX idx_status (status),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token配置表';

-- API 接口配置表
CREATE TABLE IF NOT EXISTS api_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT 'API名称',
    description VARCHAR(500) COMMENT 'API描述',
    token_config_id BIGINT COMMENT '关联的Token配置ID',
    api_url VARCHAR(500) NOT NULL COMMENT 'API接口URL',
    api_method VARCHAR(10) DEFAULT 'GET' COMMENT '请求方式: GET, POST',
    api_params TEXT COMMENT 'API参数(JSON)',
    api_headers TEXT COMMENT 'API请求头(JSON)',
    data_extract_path VARCHAR(200) COMMENT '数据提取路径(JSONPath)',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-有效, INACTIVE-无效',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_token_config_id (token_config_id),
    INDEX idx_status (status),
    INDEX idx_name (name),
    FOREIGN KEY (token_config_id) REFERENCES token_config(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API接口配置表';

-- 字段映射配置表
CREATE TABLE IF NOT EXISTS field_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    api_config_id BIGINT NOT NULL COMMENT '关联的API配置ID',
    source_field VARCHAR(100) NOT NULL COMMENT '源字段名',
    source_json_path VARCHAR(200) COMMENT '源字段JSONPath',
    target_field VARCHAR(100) NOT NULL COMMENT '目标表字段名',
    target_type VARCHAR(50) DEFAULT 'VARCHAR(255)' COMMENT '目标字段类型',
    is_unique_key TINYINT(1) DEFAULT 0 COMMENT '是否唯一键: 0-否, 1-是',
    field_order INT DEFAULT 0 COMMENT '字段顺序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_api_config_id (api_config_id),
    FOREIGN KEY (api_config_id) REFERENCES api_config(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段映射配置表';

-- 定时任务配置表
CREATE TABLE IF NOT EXISTS scheduled_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_name VARCHAR(100) NOT NULL COMMENT '任务名称',
    task_code VARCHAR(100) NOT NULL COMMENT '任务编码',
    api_config_id BIGINT COMMENT '关联的API配置ID',
    field_mapping_id BIGINT COMMENT '关联的字段映射配置ID',
    target_table VARCHAR(100) COMMENT '目标表名',
    cron_expression VARCHAR(100) NOT NULL COMMENT 'Cron表达式',
    task_status VARCHAR(20) DEFAULT 'READY' COMMENT '任务状态: READY-就绪, RUNNING-运行中, STOPPED-已停止',
    enable_status VARCHAR(20) DEFAULT 'DISABLED' COMMENT '启用状态: ENABLED-已启用, DISABLED-已禁用',
    last_execute_time DATETIME COMMENT '上次执行时间',
    next_execute_time DATETIME COMMENT '下次执行时间',
    execute_count INT DEFAULT 0 COMMENT '执行次数',
    success_count INT DEFAULT 0 COMMENT '成功次数',
    fail_count INT DEFAULT 0 COMMENT '失败次数',
    last_duration BIGINT COMMENT '上次执行耗时(毫秒)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注',
    INDEX idx_task_code (task_code),
    INDEX idx_enable_status (enable_status),
    INDEX idx_last_execute_time (last_execute_time),
    UNIQUE KEY uk_task_code (task_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务配置表';

-- 任务执行日志表
CREATE TABLE IF NOT EXISTS task_execution_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT '关联的任务ID',
    task_name VARCHAR(100) COMMENT '任务名称',
    batch_id VARCHAR(50) NOT NULL COMMENT '批次ID',
    execute_status VARCHAR(20) NOT NULL COMMENT '执行状态: RUNNING-运行中, SUCCESS-成功, FAILED-失败',
    start_time VARCHAR(30) COMMENT '开始时间',
    end_time VARCHAR(30) COMMENT '结束时间',
    duration BIGINT COMMENT '执行耗时(毫秒)',
    total_records INT DEFAULT 0 COMMENT '总记录数',
    new_insert_count INT DEFAULT 0 COMMENT '新增插入数',
    existing_count INT DEFAULT 0 COMMENT '已存在数',
    request_body TEXT COMMENT '请求报文',
    response_body TEXT COMMENT '响应报文',
    error_message TEXT COMMENT '错误信息',
    stack_trace TEXT COMMENT '堆栈信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_batch_id (batch_id),
    INDEX idx_execute_status (execute_status),
    INDEX idx_create_time (create_time),
    FOREIGN KEY (task_id) REFERENCES scheduled_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志表';

-- 插入示例数据
INSERT INTO token_config (name, token_type, token_url, token_method, token_params, token_extract_path, default_expires_seconds, status) VALUES
('示例Token配置', 'DYNAMIC', 'http://example.com/oauth/token', 'POST', '{"grant_type": "client_credentials", "client_id": "test", "client_secret": "secret"}', '$.access_token', 7200, 'ACTIVE');

INSERT INTO api_config (name, description, token_config_id, api_url, api_method, data_extract_path, status) VALUES
('示例API接口', '这是一个示例API接口', 1, 'http://example.com/api/data', 'GET', '$.data.list', 'ACTIVE');

INSERT INTO field_mapping (api_config_id, source_field, source_json_path, target_field, target_type, is_unique_key, field_order) VALUES
(1, 'id', '$.id', 'id', 'BIGINT', 1, 1),
(1, 'name', '$.name', 'name', 'VARCHAR(255)', 0, 2),
(1, 'created_at', '$.created_at', 'created_at', 'DATETIME', 0, 3);

INSERT INTO scheduled_task (task_name, task_code, api_config_id, field_mapping_id, target_table, cron_expression, enable_status, task_status) VALUES
('示例定时任务', 'TASK_001', 1, 1, 'ods_example_data', '0 0 */5 * * *', 'ENABLED', 'READY');
