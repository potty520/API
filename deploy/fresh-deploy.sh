#!/usr/bin/env bash
# ============================================================
#  全新部署一键脚本 (Linux)
#  用法: 上传 jar + 本脚本到服务器, 执行 ./fresh-deploy.sh
#  前提: MySQL 已安装运行; 本脚本自动建库建账号(需 root 权限执行 mysql)
# ============================================================
set -euo pipefail

APP_DIR="/opt/json-ingestion-system"
JAR="$APP_DIR/json-ingestion-system-1.0.0.jar"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
APP_DB_PASS="${APP_DB_PASS:-CHANGE_ME_STRONG_PASSWORD}"   # 部署前必改
MYSQL_ROOT=""

echo "== [1/5] 建目录 =="
mkdir -p "$APP_DIR/data" "$APP_DIR/logs" "$APP_DIR/config"

echo "== [2/5] 初始化数据库与账号 =="
if [ -n "${MYSQL_ROOT_PASS:-}" ]; then MYSQL_ROOT="-p$MYSQL_ROOT_PASS"; fi
$MYSQL_BIN -uroot $MYSQL_ROOT <<SQL
CREATE DATABASE IF NOT EXISTS json_ingestion CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS json_ingestion_target CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'json_ingestion'@'localhost' IDENTIFIED BY '$APP_DB_PASS';
CREATE USER IF NOT EXISTS 'json_ingestion'@'127.0.0.1' IDENTIFIED BY '$APP_DB_PASS';
CREATE USER IF NOT EXISTS 'json_ingestion'@'%' IDENTIFIED BY '$APP_DB_PASS';
GRANT ALL PRIVILEGES ON json_ingestion.* TO 'json_ingestion'@'localhost';
GRANT ALL PRIVILEGES ON json_ingestion_target.* TO 'json_ingestion'@'localhost';
GRANT ALL PRIVILEGES ON json_ingestion.* TO 'json_ingestion'@'127.0.0.1';
GRANT ALL PRIVILEGES ON json_ingestion_target.* TO 'json_ingestion'@'127.0.0.1';
GRANT ALL PRIVILEGES ON json_ingestion.* TO 'json_ingestion'@'%';
GRANT ALL PRIVILEGES ON json_ingestion_target.* TO 'json_ingestion'@'%';
FLUSH PRIVILEGES;
SQL
echo "   数据库与账号创建完成"

echo "== [3/5] 写入外部配置 =="
cat > "$APP_DIR/config/application.yml" <<CFG
META_DB_URL: jdbc:mysql://${META_DB_HOST:-127.0.0.1}:${META_DB_PORT:-3306}/json_ingestion?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
META_DB_USER: json_ingestion
META_DB_PASSWORD: $APP_DB_PASS
TARGET_DB_HOST: ${META_DB_HOST:-127.0.0.1}
TARGET_DB_PORT: ${META_DB_PORT:-3306}
TARGET_DB_NAME: json_ingestion_target
TARGET_DB_USER: json_ingestion
TARGET_DB_PASSWORD: $APP_DB_PASS
INGESTION_SECRET_FILE: $APP_DIR/data/.secret
INGESTION_ALLOW_PRIVATE_URLS: "${INGESTION_ALLOW_PRIVATE_URLS:-true}"
INGESTION_PORT: ${INGESTION_PORT:-3100}
SERVER_ADDRESS: 0.0.0.0
CFG
echo "   配置写入 $APP_DIR/config/application.yml"

echo "== [4/5] 写 systemd 服务 =="
cat > /etc/systemd/system/json-ingestion.service <<EOF
[Unit]
Description=JSON Ingestion System
After=network.target mysqld.service
Wants=mysqld.service

[Service]
Type=simple
WorkingDirectory=$APP_DIR
ExecStart=/usr/bin/java -jar $JAR
Restart=on-failure
RestartSec=10
NoNewPrivileges=true
PrivateTmp=true
ReadWritePaths=$APP_DIR

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable json-ingestion

echo "== [5/5] 启动 =="
systemctl start json-ingestion
sleep 25
systemctl is-active json-ingestion
echo "健康检查:"
curl -s -m 5 http://127.0.0.1:${INGESTION_PORT:-3100}/api/health || echo " (稍后重试)"
echo
echo "=== 部署完成 ==="
echo "访问: http://<服务器IP>:${INGESTION_PORT:-3100}  (账号 admin/admin123, 首次登录请修改)"