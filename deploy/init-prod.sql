-- ============================================================
--  全新部署初始化脚本 (用 MySQL root 执行一次)
--  用法: mysql -uroot -p < init-prod.sql
--  执行前把 CHANGE_ME 替换成你的强密码
-- ============================================================
CREATE DATABASE IF NOT EXISTS json_ingestion CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS json_ingestion_target CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 应用账号(替换 CHANGE_ME 为强密码, 建议 16 位以上含大小写数字特殊字符)
CREATE USER IF NOT EXISTS 'json_ingestion'@'localhost' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';
CREATE USER IF NOT EXISTS 'json_ingestion'@'127.0.0.1' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';
CREATE USER IF NOT EXISTS 'json_ingestion'@'%' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';

GRANT ALL PRIVILEGES ON json_ingestion.* TO 'json_ingestion'@'localhost';
GRANT ALL PRIVILEGES ON json_ingestion_target.* TO 'json_ingestion'@'localhost';
GRANT ALL PRIVILEGES ON json_ingestion.* TO 'json_ingestion'@'127.0.0.1';
GRANT ALL PRIVILEGES ON json_ingestion_target.* TO 'json_ingestion'@'127.0.0.1';
GRANT ALL PRIVILEGES ON json_ingestion.* TO 'json_ingestion'@'%';
GRANT ALL PRIVILEGES ON json_ingestion_target.* TO 'json_ingestion'@'%';
FLUSH PRIVILEGES;

-- 验证
SHOW DATABASES;