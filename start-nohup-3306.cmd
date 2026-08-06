@echo off
setlocal
cd /d "%~dp0"
set "META_DB_URL=jdbc:mysql://127.0.0.1:3306/json_ingestion?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
set "META_DB_USER=json_ingestion"
rem 部署前请设置环境变量 META_DB_PASSWORD / TARGET_DB_PASSWORD (setx META_DB_PASSWORD "你的强密码")
set "META_DB_PASSWORD=%META_DB_PASSWORD%"
set "TARGET_DB_HOST=127.0.0.1"
set "TARGET_DB_PORT=3306"
set "TARGET_DB_NAME=json_ingestion_target"
set "TARGET_DB_USER=json_ingestion"
set "TARGET_DB_PASSWORD=%TARGET_DB_PASSWORD%"
set "INGESTION_SECRET_FILE=%CD%\data\.secret"
set "INGESTION_ALLOW_PRIVATE_URLS=true"
if not exist "%CD%\logs" mkdir "%CD%\logs"
start "JSON Ingestion 3306" /min "C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot\bin\java.exe" -jar "%CD%\backend\target\json-ingestion-system-1.0.0.jar"
exit /b 0

