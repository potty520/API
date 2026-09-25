CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role_name VARCHAR(40) NOT NULL,
    active TINYINT NOT NULL DEFAULT 1,
    must_change_password TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS task_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(500) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS data_source_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    code VARCHAR(80) NOT NULL UNIQUE,
    db_type VARCHAR(24) NOT NULL,
    host VARCHAR(255) NOT NULL DEFAULT '',
    port INT NULL,
    database_name VARCHAR(255) NOT NULL DEFAULT '',
    schema_name VARCHAR(255) NOT NULL DEFAULT '',
    service_name VARCHAR(255) NOT NULL DEFAULT '',
    jdbc_url VARCHAR(1000) NOT NULL DEFAULT '',
    username VARCHAR(255) NOT NULL DEFAULT '',
    password_enc TEXT NULL,
    options_json LONGTEXT NOT NULL,
    active TINYINT NOT NULL DEFAULT 1,
    last_test_status VARCHAR(40) NOT NULL DEFAULT '未测试',
    last_test_message VARCHAR(1000) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interface_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(160) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    group_id BIGINT NULL,
    url VARCHAR(2000) NOT NULL,
    method VARCHAR(12) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    enabled TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL DEFAULT '未执行',
    headers_json LONGTEXT NOT NULL,
    query_json LONGTEXT NOT NULL,
    body_json LONGTEXT NOT NULL,
    auth_type VARCHAR(30) NOT NULL DEFAULT 'none',
    auth_json_enc LONGTEXT NULL,
    root_path VARCHAR(500) NOT NULL DEFAULT '',
    datasource_id BIGINT NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    unique_key VARCHAR(255) NOT NULL DEFAULT '',
    cron_expr VARCHAR(120) NOT NULL,
    retry_count INT NOT NULL DEFAULT 2,
    retry_interval_sec INT NOT NULL DEFAULT 5,
    timeout_sec INT NOT NULL DEFAULT 30,
    source_label VARCHAR(500) NOT NULL DEFAULT '',
    next_run_at DATETIME NULL,
    last_run_at DATETIME NULL,
    last_success_at DATETIME NULL,
    created_by VARCHAR(80) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_task_schedule(enabled, next_run_at),
    CONSTRAINT fk_task_group FOREIGN KEY (group_id) REFERENCES task_group(id),
    CONSTRAINT fk_task_datasource FOREIGN KEY (datasource_id) REFERENCES data_source_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS execution_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_no VARCHAR(80) NOT NULL UNIQUE,
    task_id BIGINT NOT NULL,
    trigger_type VARCHAR(40) NOT NULL,
    sync_mode VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    started_at DATETIME NOT NULL,
    ended_at DATETIME NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    http_status INT NULL,
    response_ms BIGINT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    total_count BIGINT NOT NULL DEFAULT 0,
    inserted_count BIGINT NOT NULL DEFAULT 0,
    updated_count BIGINT NOT NULL DEFAULT 0,
    skipped_count BIGINT NOT NULL DEFAULT 0,
    failed_count BIGINT NOT NULL DEFAULT 0,
    empty_count BIGINT NOT NULL DEFAULT 0,
    batch_id VARCHAR(80) NOT NULL,
    target_table VARCHAR(128) NOT NULL,
    schema_changes LONGTEXT NOT NULL,
    error_message TEXT NULL,
    error_stack LONGTEXT NULL,
    requested_by VARCHAR(80) NOT NULL,
    detail_json LONGTEXT NOT NULL,
    INDEX idx_run_task_started(task_id, started_at),
    CONSTRAINT fk_run_task FOREIGN KEY (task_id) REFERENCES interface_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS schema_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    version_no INT NOT NULL,
    structure_json LONGTEXT NOT NULL,
    mapping_json LONGTEXT NOT NULL,
    batch_id VARCHAR(80) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_schema_task(task_id, version_no),
    CONSTRAINT fk_schema_task FOREIGN KEY (task_id) REFERENCES interface_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alert_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NULL,
    run_id BIGINT NULL,
    level_name VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT '未处理',
    created_at DATETIME NOT NULL,
    resolved_at DATETIME NULL,
    INDEX idx_alert_status(status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(80) NOT NULL,
    action_name VARCHAR(160) NOT NULL,
    module_name VARCHAR(100) NOT NULL,
    detail_text TEXT NULL,
    ip_address VARCHAR(80) NOT NULL DEFAULT '',
    prev_hash VARCHAR(64) NOT NULL DEFAULT '',
    row_hash VARCHAR(64) NOT NULL DEFAULT '',
    chain_version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    INDEX idx_audit_created(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_setting (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(1000) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
