#!/usr/bin/env bash
# ============================================================
#  全新部署一键脚本 (Linux)
#  用法: 上传 jar 与 deploy/ 目录到服务器, 以 root 执行
#        sudo APP_DB_PASS='你的强密码' ./fresh-deploy.sh
#  前提: MySQL 已安装并运行; jar 已放到 $APP_DIR 下
#  说明: 不提供弱默认密码; 密码只写入 0600 的 env 文件, 由 systemd EnvironmentFile 加载
# ============================================================
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/json-ingestion-system}"
APP_USER="${APP_USER:-ingestion}"
JAR="${APP_DIR}/json-ingestion-system-1.0.0.jar"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
DB_HOST="${META_DB_HOST:-127.0.0.1}"
DB_PORT="${META_DB_PORT:-3306}"
DB_USER="json_ingestion"
INGESTION_PORT="${INGESTION_PORT:-3100}"
SERVER_ADDRESS="${SERVER_ADDRESS:-127.0.0.1}"
UNIT_SRC="$(cd "$(dirname "$0")" && pwd)/json-ingestion.service"

fail() { echo "[ERROR] $*" >&2; exit 1; }

if [ "$(id -u)" -ne 0 ]; then fail "请用 root 执行(需要建库建账号并写 /etc/systemd/system)"; fi
if [ ! -f "$JAR" ]; then fail "找不到 jar: $JAR"; fi
if [ ! -f "$UNIT_SRC" ]; then fail "找不到 systemd 单元模板: $UNIT_SRC (请与本脚本放在同一目录)"; fi
if [ -z "${APP_DB_PASS:-}" ]; then fail "请先设置 APP_DB_PASS, 例如: sudo APP_DB_PASS='你的强密码' $0"; fi
if [ "$APP_DB_PASS" = "CHANGE_ME_STRONG_PASSWORD" ]; then fail "APP_DB_PASS 仍是示例值, 请换成真实强密码"; fi
if [ "${#APP_DB_PASS}" -lt 12 ]; then fail "APP_DB_PASS 至少 12 位"; fi
# 密码会被写进 SQL 与 env 文件, 挡掉会破坏引号或触发变量展开的字符
for bad in "'" '"' '`' '$' '\'; do
  case "$APP_DB_PASS" in
    *"$bad"*) fail "APP_DB_PASS 不能包含 ${bad} 字符" ;;
  esac
done

echo "== [1/6] 建目录与专用账号 =="
if ! id -u "$APP_USER" >/dev/null 2>&1; then useradd -r -s /sbin/nologin -d "$APP_DIR" "$APP_USER"; fi
mkdir -p "$APP_DIR/data" "$APP_DIR/logs" "$APP_DIR/config"

echo "== [2/6] 初始化数据库与账号(仅 localhost / 127.0.0.1) =="
ROOT_CNF="$(mktemp)"
chmod 600 "$ROOT_CNF"
trap 'rm -f "$ROOT_CNF"' EXIT
{
  echo "[client]"
  echo "user=root"
  if [ -n "${MYSQL_ROOT_PASS:-}" ]; then printf 'password=%s\n' "$MYSQL_ROOT_PASS"; fi
} > "$ROOT_CNF"

"$MYSQL_BIN" --defaults-extra-file="$ROOT_CNF" --host="$DB_HOST" --port="$DB_PORT" <<SQL
CREATE DATABASE IF NOT EXISTS json_ingestion CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS json_ingestion_target CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${APP_DB_PASS}';
CREATE USER IF NOT EXISTS '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${APP_DB_PASS}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${APP_DB_PASS}';
ALTER USER '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${APP_DB_PASS}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, LOCK TABLES,
      CREATE VIEW, SHOW VIEW, CREATE ROUTINE, ALTER ROUTINE, EXECUTE
  ON json_ingestion.* TO '${DB_USER}'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, LOCK TABLES
  ON json_ingestion_target.* TO '${DB_USER}'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, LOCK TABLES,
      CREATE VIEW, SHOW VIEW, CREATE ROUTINE, ALTER ROUTINE, EXECUTE
  ON json_ingestion.* TO '${DB_USER}'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, LOCK TABLES
  ON json_ingestion_target.* TO '${DB_USER}'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
rm -f "$ROOT_CNF"
trap - EXIT

echo "== [3/6] 写入环境变量文件(0600) =="
ENV_FILE="${APP_DIR}/config/json-ingestion.env"
(
  umask 077
  cat > "$ENV_FILE" <<CFG
META_DB_URL=jdbc:mysql://${DB_HOST}:${DB_PORT}/json_ingestion?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
META_DB_USER=${DB_USER}
META_DB_PASSWORD=${APP_DB_PASS}
TARGET_DB_HOST=${DB_HOST}
TARGET_DB_PORT=${DB_PORT}
TARGET_DB_NAME=json_ingestion_target
TARGET_DB_USER=${DB_USER}
TARGET_DB_PASSWORD=${APP_DB_PASS}
INGESTION_SECRET_FILE=${APP_DIR}/data/.secret
INGESTION_ALLOW_PRIVATE_URLS=${INGESTION_ALLOW_PRIVATE_URLS:-true}
INGESTION_PORT=${INGESTION_PORT}
SERVER_ADDRESS=${SERVER_ADDRESS}
INGESTION_SEED_DEMO=${INGESTION_SEED_DEMO:-false}
INGESTION_DEMO_API=${INGESTION_DEMO_API:-false}
INGESTION_TRUST_PROXY=${INGESTION_TRUST_PROXY:-true}
CFG
)
chown "$APP_USER:$APP_USER" "$ENV_FILE"
chmod 600 "$ENV_FILE"

echo "== [4/6] 收紧目录权限 =="
chown -R "$APP_USER:$APP_USER" "$APP_DIR"
chmod 700 "$APP_DIR/data" "$APP_DIR/config"

echo "== [5/6] 安装 systemd 服务 =="
install -m 644 "$UNIT_SRC" /etc/systemd/system/json-ingestion.service
systemctl daemon-reload
systemctl enable json-ingestion >/dev/null 2>&1

echo "== [6/6] 启动并等待就绪 =="
systemctl restart json-ingestion
READY=0
for _ in $(seq 1 60); do
  if curl -fsS -m 3 "http://127.0.0.1:${INGESTION_PORT}/api/health" >/dev/null 2>&1; then READY=1; break; fi
  sleep 2
done
if [ "$READY" -ne 1 ]; then
  echo "[ERROR] 探测 120s 后仍未就绪, 请执行: journalctl -u json-ingestion -n 200" >&2
  systemctl --no-pager -l status json-ingestion || true
  exit 1
fi

echo
echo "=== 部署完成 ==="
echo "监听地址: ${SERVER_ADDRESS}:${INGESTION_PORT}"
if [ "$SERVER_ADDRESS" = "127.0.0.1" ]; then
  echo "  (只监听回环, 请按 deploy/nginx-json-ingestion.conf 配好 HTTPS 反代后再对外访问)"
fi
echo "初始账号: admin / config / runner / viewer"
echo "初始密码: 未通过 INGESTION_INIT_*_PASSWORD 指定, 已随机生成并只输出一次, 用下面命令查看:"
echo "  journalctl -u json-ingestion | grep '一次性初始密码'"
echo "所有初始账号首次登录都会被强制修改密码"
