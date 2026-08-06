@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem ==== ???? 3306 MySQL ??(??? 3307 ?? MySQL)====
set "META_DB_URL=jdbc:mysql://127.0.0.1:3306/json_ingestion?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
set "META_DB_USER=json_ingestion"
rem 部署前请设置环境变量 META_DB_PASSWORD / TARGET_DB_PASSWORD (setx META_DB_PASSWORD "你的强密码")
set "META_DB_PASSWORD=%META_DB_PASSWORD%"
set "TARGET_DB_HOST=127.0.0.1"
set "TARGET_DB_PORT=3306"
set "TARGET_DB_NAME=json_ingestion_target"
set "TARGET_DB_USER=json_ingestion"
set "TARGET_DB_PASSWORD=%TARGET_DB_PASSWORD%"
set "INGESTION_ALLOW_PRIVATE_URLS=true"

set "JAVA_EXE="
if defined JAVA17_HOME if exist "%JAVA17_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA17_HOME%\bin\java.exe"
for /D %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if exist "%%~fD\bin\java.exe" set "JAVA_EXE=%%~fD\bin\java.exe"
if not defined JAVA_EXE if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
  echo [ERROR] Java 17 was not found. Set JAVA17_HOME and retry.
  exit /b 1
)

set "JAR=%CD%\backend\target\json-ingestion-system-1.0.0.jar"
if not exist "%JAR%" (
  echo [ERROR] JAR not found: %JAR%
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r=Invoke-RestMethod 'http://127.0.0.1:3100/api/health' -TimeoutSec 2; if($r.ok){exit 0} } catch {}; exit 1"
if not errorlevel 1 goto open_app

echo Starting Spring Boot on http://127.0.0.1:3100 (MySQL 3306)...
if not exist "%CD%\logs" mkdir "%CD%\logs"
start "JSON Ingestion System" /min "%JAVA_EXE%" -jar "%JAR%"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ready=$false; 1..60 | ForEach-Object { try { $r=Invoke-RestMethod 'http://127.0.0.1:3100/api/health' -TimeoutSec 2; if($r.ok){$ready=$true; return} } catch {}; Start-Sleep -Seconds 1 }; if(-not $ready){exit 1}"
if errorlevel 1 (
  echo [ERROR] Application startup timed out. See logs\json-ingestion.log.
  exit /b 1
)

:open_app
echo JSON ingestion system is ready: http://127.0.0.1:3100
start "" "http://127.0.0.1:3100"
exit /b 0


