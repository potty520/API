@echo off
chcp 65001 >nul
setlocal EnableExtensions
rem ============================================================
rem  JSON 接入系统 启动脚本 (复用本机已有的 MySQL 3306)
rem  与 start-system.cmd 的区别: 不启动/不管理专用 3307 实例
rem  密码: 只从环境变量 META_DB_PASSWORD / TARGET_DB_PASSWORD 读取, 不写死在脚本里
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
rem 应用直接对外(没有 Nginx 反代)时必须为 false, 否则 X-Forwarded-For 可被伪造
if not defined INGESTION_TRUST_PROXY set "INGESTION_TRUST_PROXY=false"

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

powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r=Invoke-RestMethod 'http://127.0.0.1:3100/api/health' -TimeoutSec 2; if($r.ok){exit 0} } catch {}; exit 1"
if not errorlevel 1 goto open_app

echo 正在启动应用 http://127.0.0.1:3100 (MySQL 3306) ...
if not exist "%CD%\logs" mkdir "%CD%\logs"
start "JSON Ingestion System" /min "%JAVA_EXE%" -jar "%JAR%"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ready=$false; 1..60 | ForEach-Object { try { $r=Invoke-RestMethod 'http://127.0.0.1:3100/api/health' -TimeoutSec 2; if($r.ok){$ready=$true; return} } catch {}; Start-Sleep -Seconds 1 }; if(-not $ready){exit 1}"
if errorlevel 1 (
  echo [ERROR] 应用启动超时, 请查看 logs\json-ingestion.log
  exit /b 1
)

:open_app
echo 系统已就绪: http://127.0.0.1:3100
echo 首次登录: 用户名 admin, 密码为启动日志中输出的一次性密码(或由 INGESTION_INIT_ADMIN_PASSWORD 指定), 登录后会强制改密
start "" "http://127.0.0.1:3100"
exit /b 0
