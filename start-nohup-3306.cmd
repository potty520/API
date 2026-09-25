@echo off
chcp 65001 >nul
setlocal EnableExtensions
rem ============================================================
rem  后台启动(相当于 nohup): 复用本机 MySQL 3306, 启动后不等待、不打开浏览器
rem  停止请运行 stop-system.cmd; 日志见 logs\json-ingestion.log
rem ============================================================
cd /d "%~dp0"

if not defined META_DB_PASSWORD (
  echo [ERROR] 未设置环境变量 META_DB_PASSWORD, 已终止
  echo         请先执行: setx META_DB_PASSWORD "你的强密码"   然后重开命令行窗口
  exit /b 1
)

set "META_DB_URL=jdbc:mysql://127.0.0.1:3306/json_ingestion?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
set "META_DB_USER=json_ingestion"
if not defined TARGET_DB_PASSWORD set "TARGET_DB_PASSWORD=%META_DB_PASSWORD%"
set "TARGET_DB_HOST=127.0.0.1"
set "TARGET_DB_PORT=3306"
set "TARGET_DB_NAME=json_ingestion_target"
set "TARGET_DB_USER=json_ingestion"
set "INGESTION_SECRET_FILE=%CD%\data\.secret"
set "INGESTION_PORT=3100"
set "SERVER_ADDRESS=127.0.0.1"
rem 内置演示接口指向 127.0.0.1, 需要放开内网地址限制; 只对接外网接口时请删掉下面这行
set "INGESTION_ALLOW_PRIVATE_URLS=true"
if not defined INGESTION_TRUST_PROXY set "INGESTION_TRUST_PROXY=false"

rem 不再写死某个具体的 JDK 安装路径, 按 JAVA17_HOME -> Adoptium 目录 -> JAVA_HOME 顺序查找
set "JAVA_EXE="
if defined JAVA17_HOME if exist "%JAVA17_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA17_HOME%\bin\java.exe"
if not defined JAVA_EXE for /D %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if exist "%%~fD\bin\java.exe" set "JAVA_EXE=%%~fD\bin\java.exe"
if not defined JAVA_EXE if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
  echo [ERROR] 未找到 Java 17, 请设置环境变量 JAVA17_HOME 后重试
  exit /b 1
)

set "JAR=%CD%\backend\target\json-ingestion-system-1.0.0.jar"
if not exist "%JAR%" (
  echo [ERROR] 未找到 JAR: %JAR%
  echo         请先运行 build-system.cmd
  exit /b 1
)

if not exist "%CD%\logs" mkdir "%CD%\logs"
start "JSON Ingestion 3306" /min "%JAVA_EXE%" -jar "%JAR%"
echo 已后台启动, 日志: logs\json-ingestion.log
echo 停止请运行 stop-system.cmd
exit /b 0
