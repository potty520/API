@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.4\bin"
if defined MYSQL_HOME set "MYSQL_BIN=%MYSQL_HOME%\bin"
set "MYSQLD=%MYSQL_BIN%\mysqld.exe"
set "MYSQL=%MYSQL_BIN%\mysql.exe"
set "MYSQLADMIN=%MYSQL_BIN%\mysqladmin.exe"
set "DATA=%CD%\mysql-data"
set "NEW_DATA=0"

if not exist "%MYSQLD%" (
  echo [ERROR] MySQL 8.4 was not found. Set MYSQL_HOME and retry.
  exit /b 1
)

netstat -ano | findstr /R /C:":3307 .*LISTENING" >nul
if not errorlevel 1 goto verify

if not exist "%DATA%\mysql" (
  echo Initializing dedicated MySQL data directory...
  if not exist "%DATA%" mkdir "%DATA%"
  "%MYSQLD%" --no-defaults --initialize-insecure --datadir="%DATA%"
  if errorlevel 1 exit /b 1
  set "NEW_DATA=1"
)

echo Starting dedicated MySQL on 127.0.0.1:3307...
start "JSON Ingestion MySQL" /min "%MYSQLD%" --no-defaults --datadir="%DATA%" --port=3307 --bind-address=127.0.0.1 --mysqlx=0 --pid-file="%CD%\mysql3307.pid" --log-error="%CD%\mysql3307.log"

for /L %%I in (1,1,30) do (
  "%MYSQLADMIN%" --protocol=tcp --host=127.0.0.1 --port=3307 --user=root ping >nul 2>&1 && goto ready
  timeout /t 1 /nobreak >nul
)
echo [ERROR] MySQL did not become ready. See mysql3307.log.
exit /b 1

:ready
if "%NEW_DATA%"=="1" (
  echo Creating application databases and account...
  "%MYSQL%" --protocol=tcp --host=127.0.0.1 --port=3307 --user=root -e "CREATE DATABASE IF NOT EXISTS json_ingestion CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE IF NOT EXISTS json_ingestion_target CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'json_ingestion'@'127.0.0.1' IDENTIFIED BY '${META_DB_PASSWORD}'; CREATE USER IF NOT EXISTS 'json_ingestion'@'localhost' IDENTIFIED BY '${META_DB_PASSWORD}'; GRANT ALL PRIVILEGES ON json_ingestion.* TO 'json_ingestion'@'127.0.0.1'; GRANT ALL PRIVILEGES ON json_ingestion_target.* TO 'json_ingestion'@'127.0.0.1'; GRANT ALL PRIVILEGES ON json_ingestion.* TO 'json_ingestion'@'localhost'; GRANT ALL PRIVILEGES ON json_ingestion_target.* TO 'json_ingestion'@'localhost'; FLUSH PRIVILEGES;"
  if errorlevel 1 exit /b 1
)

:verify
"%MYSQLADMIN%" --protocol=tcp --host=127.0.0.1 --port=3307 --user=json_ingestion --password=${META_DB_PASSWORD} ping >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Port 3307 is not the expected JSON ingestion database.
  exit /b 1
)
echo MySQL 3307 is ready.
exit /b 0
