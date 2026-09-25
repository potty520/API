-- ============================================================
--  全新部署初始化脚本 (用 MySQL root 执行一次)
--  用法: mysql -uroot -p < init-prod.sql
--  执行前: 把下面所有 __APP_DB_PASSWORD__ 替换成强密码
--          (建议 16 位以上含大小写字母/数字/符号; 不要包含单引号 ' 和反斜杠 \ )
--  安全: 只创建 localhost / 127.0.0.1 两个来源, 不创建 '%'。
--        应用与数据库同机部署即可; 确需跨机访问时再按最小网段单独授权。
-- ============================================================
CREATE DATABASE IF NOT EXISTS json_ingestion CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS json_ingestion_target CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'json_ingestion'@'localhost' IDENTIFIED BY '__APP_DB_PASSWORD__';
CREATE USER IF NOT EXISTS 'json_ingestion'@'127.0.0.1' IDENTIFIED BY '__APP_DB_PASSWORD__';
-- 重复执行时同步为新密码
ALTER USER 'json_ingestion'@'localhost' IDENTIFIED BY '__APP_DB_PASSWORD__';
ALTER USER 'json_ingestion'@'127.0.0.1' IDENTIFIED BY '__APP_DB_PASSWORD__';

-- 元数据库: schema.sql 与 Quartz 需要建表/改表, 外加业务读写
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, LOCK TABLES,
      CREATE VIEW, SHOW VIEW, CREATE ROUTINE, ALTER ROUTINE, EXECUTE
  ON json_ingestion.* TO 'json_ingestion'@'localhost';
-- 目标库: 自动建表 + 数据读写, 不给 GRANT OPTION / SUPER 等管理权限
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, LOCK TABLES
  ON json_ingestion_target.* TO 'json_ingestion'@'localhost';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, LOCK TABLES,
      CREATE VIEW, SHOW VIEW, CREATE ROUTINE, ALTER ROUTINE, EXECUTE
  ON json_ingestion.* TO 'json_ingestion'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, LOCK TABLES
  ON json_ingestion_target.* TO 'json_ingestion'@'127.0.0.1';

FLUSH PRIVILEGES;

-- 验证: host 应只有 localhost 与 127.0.0.1, 不应出现 %
SELECT user, host FROM mysql.user WHERE user = 'json_ingestion';
SHOW GRANTS FOR 'json_ingestion'@'localhost';
