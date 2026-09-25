#!/usr/bin/env bash
# ============================================================
#  JSON 接入系统 每日备份 (Linux)
#  内容: 元数据库 + 目标库 + AES 主密钥 data/.secret (三者必须一起备份, 一起恢复)
#  保留: 最近 14 份, 自动清理更早的
#  建议: crontab 每天 02:00 -> 0 2 * * * /opt/json-ingestion-system/backup.sh >> /var/log/json-ingestion-backup.log 2>&1
#  密码: 只从环境变量 META_DB_PASSWORD / TARGET_DB_PASSWORD 读取, 不写死在脚本里
#  说明: 通过 MYSQL_PWD 传密码, 避免出现在 ps 的命令行参数中
# ============================================================
set -euo pipefail

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKUP_ROOT="${BASE_DIR}/backups"
KEEP=14
STAMP="$(date +%Y%m%d_%H%M%S)"
DIR="${BACKUP_ROOT}/${STAMP}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-json_ingestion}"
META_DB="${META_DB:-json_ingestion}"
TARGET_DB="${TARGET_DB:-json_ingestion_target}"
SECRET_FILE="${INGESTION_SECRET_FILE:-${BASE_DIR}/data/.secret}"

# set -u 下必须给默认值, 否则变量未导出会直接 unbound variable 退出
META_DB_PASSWORD="${META_DB_PASSWORD:-}"
TARGET_DB_PASSWORD="${TARGET_DB_PASSWORD:-${META_DB_PASSWORD}}"

fail() { echo "[ERROR] $*" >&2; exit 1; }

[ -n "${META_DB_PASSWORD}" ] || fail "未设置环境变量 META_DB_PASSWORD, 备份终止"
[ -n "${TARGET_DB_PASSWORD}" ] || fail "未设置 TARGET_DB_PASSWORD(或 META_DB_PASSWORD), 备份终止"
command -v mysqldump >/dev/null 2>&1 || fail "找不到 mysqldump, 请先安装 MySQL 客户端"

mkdir -p "${DIR}"
chmod 700 "${DIR}"

dump() {
  mysqldump --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" \
    --default-character-set=utf8mb4 --single-transaction --routines --triggers --no-tablespaces \
    "$1" > "$2"
  [ -s "$2" ] || fail "导出文件为空: $2"
}

echo "[1/3] 备份元数据库 ${META_DB} ..."
export MYSQL_PWD="${META_DB_PASSWORD}"
dump "${META_DB}" "${DIR}/meta_${META_DB}.sql"

echo "[2/3] 备份目标库 ${TARGET_DB} ..."
export MYSQL_PWD="${TARGET_DB_PASSWORD}"
dump "${TARGET_DB}" "${DIR}/target_${TARGET_DB}.sql"
unset MYSQL_PWD

echo "[3/3] 备份 AES 主密钥 ..."
[ -f "${SECRET_FILE}" ] || fail "未找到主密钥文件 ${SECRET_FILE}, 缺它无法解密数据源密码; 请确认 INGESTION_SECRET_FILE"
install -m 600 "${SECRET_FILE}" "${DIR}/secret.txt"

# 只保留最近 KEEP 份, 且只允许删除备份根目录下的条目
ls -1dt "${BACKUP_ROOT}"/*/ 2>/dev/null | tail -n +$((KEEP + 1)) | while read -r old; do
  case "${old}" in
    "${BACKUP_ROOT}"/*) echo "清理过期备份 ${old}"; rm -rf -- "${old}" ;;
    *) echo "[WARN] 跳过非备份目录 ${old}" >&2 ;;
  esac
done

echo "备份完成: ${DIR}"
echo "提醒: 请把备份目录再同步到异地(NAS/对象存储), 恢复时 sql 与 secret.txt 必须一起使用"
