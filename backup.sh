#!/usr/bin/env bash
# ============================================================
#  JSON ???? ???? (Linux)
#  ??: ???? + ??? + AES ??? data/.secret
#  ??: ?? 14 ?, ????
#  ??: ?? crontab, ???? 02:00: 0 2 * * * /opt/json-ingestion-system/backup.sh
#  ???????? DB_PASS ???
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
DB_PASS="${DB_PASS:-${META_DB_PASSWORD}}"
META_DB="${META_DB:-json_ingestion}"
TARGET_DB="${TARGET_DB:-json_ingestion_target}"

mkdir -p "${DIR}"

echo "[1/3] dump meta db ${META_DB} ..."
mysqldump --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" --password="${DB_PASS}" \
  --default-character-set=utf8mb4 --single-transaction --routines --triggers --no-tablespaces \
  "${META_DB}" > "${DIR}/meta_${META_DB}.sql"

echo "[2/3] dump target db ${TARGET_DB} ..."
mysqldump --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" --password="${DB_PASS}" \
  --default-character-set=utf8mb4 --single-transaction --routines --triggers --no-tablespaces \
  "${TARGET_DB}" > "${DIR}/data_${TARGET_DB}.sql"

echo "[3/3] backup AES master key ..."
cp "${BASE_DIR}/data/.secret" "${DIR}/secret.txt"

echo "backup done: ${DIR}"
ls -la "${DIR}"

# ?????(???? KEEP ?)
cd "${BACKUP_ROOT}" || exit 0
ls -1d */ 2>/dev/null | sort -r | tail -n +$((KEEP+1)) | while read -r old; do
  echo "clean old backup: ${BACKUP_ROOT}/${old}"
  rm -rf "${BACKUP_ROOT}/${old}"
done
