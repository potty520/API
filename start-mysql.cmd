@echo off
chcp 65001 >nul
setlocal EnableExtensions
rem ============================================================
rem  JSON 接入系统 专用 MySQL (127.0.0.1:3307)
rem  与本机已有的 3306 实例互不影响, 数据目录为本项目下的 mysql-data
rem  密码只从环境变量 META_DB_PASSWORD 读取, 不写死在脚本里
rem  注意: 密码中不要包含 & | < > ^ " 等 cmd 特殊字符
rem ============================================================
cd /d "%~dp0"

set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.4\bin"
if defined MYSQL_HOME set "MYSQL_BIN=%MYSQL_HOME%\bin"
set "MYSQLD=%MYSQL_BIN%\mysqld.exe"
set "MYSQL=%MYSQL_BIN%\mysql.exe"
set "MYSQLADMIN=%MYSQL_BIN%\mysqladmin.exe"
set "DATA=%CD%\mysql-data"
set "PORT=3307"
set "NEW_DATA=0"

if not exist "%MYSQLD%" (
  echo [ERROR] 未找到 MySQL 8.4, 请设置环境变量 MYSQL_HOME 后重试
  exit /b 1
)

if not defined META_DB_PASSWORD (
  echo [ERROR] 未设置环境变量 META_DB_PASSWORD, 已终止
  echo         请先执行: setx META_DB_PASSWORD "你的强密码"   然后重开命令行窗口
  exit /b 1
)

netstat -ano | findstr /R /C:":%PORT% .*LISTENING" >nul
if not errorlevel 1 goto verify

if not exist "%DATA%\mysql" (
  echo 正在初始化专用 MySQL 数据目录 ...
  if not exist "%DATA%" mkdir "%DATA%"
  "%MYSQLD%" --no-defaults --initialize-insecure --datadir="%DATA%"
  if errorlevel 1 exit /b 1
  set "NEW_DATA=1"
)

echo 正在启动专用 MySQL 127.0.0.1:%PORT% ...
start "JSON Ingestion MySQL" /min "%MYSQLD%" --no-defaults --datadir="%DATA%" --port=%PORT% --bind-address=127.0.0.1 --mysqlx=0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci --pid-file="%CD%\mysql3307.pid" --log-error="%CD%\mysql3307.log"

for /L %%I in (1,1,30) do (
  "%MYSQLADMIN%" --protocol=tcp --host=127.0.0.1 --port=%PORT% --user=root ping >nul 2>&1 && goto ready
  timeout /t 1 /nobreak >nul
)
echo [ERROR] MySQL 启动超时, 请查看 mysql3307.log
exit /b 1

:ready
if not "%NEW_DATA%"=="1" goto verify
echo 正在创建应用数据库与账号 ...
rem 账号密码通过临时配置文件与 SQL 文件传递, 避免出现在命令行和进程列表里
set "CNF=%TEMP%\ji-mysql-init-%RANDOM%%RANDOM%.cnf"
set "SQL=%TEMP%\ji-mysql-init-%RANDOM%%RANDOM%.sql"
> "%CNF%" echo [client]
>>"%CNF%" echo user=root
>>"%CNF%" echo host=127.0.0.1
>>"%CNF%" echo port=%PORT%
>>"%CNF%" echo protocol=tcp
>  "%SQL%" echo CREATE DATABASE IF NOT EXISTS json_ingestion CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
>> "%SQL%" echo CREATE DATABASE IF NOT EXISTS json_ingestion_target CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
>> "%SQL%" echo CREATE USER IF NOT EXISTS 'json_ingestion'@'127.0.0.1' IDENTIFIED BY '%META_DB_PASSWORD%';
>> "%SQL%" echo CREATE USER IF NOT EXISTS 'json_ingestion'@'localhost' IDENTIFIED BY '%META_DB_PASSWORD%';
>> "%SQL%" echo GRANT ALL PRIVILEGES ON json_ingestion.* TO 'json_ingestion'@'127.0.0.1';
>> "%SQL%" echo GRANT ALL PRIVILEGES ON json_ingestion_target.* TO 'json_ingestion'@'127.0.0.1';
>> "%SQL%" echo GRANT ALL PRIVILEGES ON json_ingestion.* TO 'json_ingestion'@'localhost';
>> "%SQL%" echo GRANT ALL PRIVILEGES ON json_ingestion_target.* TO 'json_ingestion'@'localhost';
>> "%SQL%" echo FLUSH PRIVILEGES;
"%MYSQL%" --defaults-extra-file="%CNF%" < "%SQL%"
if errorlevel 1 set "INIT_FAILED=1"
del /q "%CNF%" "%SQL%" >nul 2>&1
if defined INIT_FAILED (
  echo [ERROR] 初始化数据库失败, 请查看上方 MySQL 报错
  exit /b 1
)

:verify
rem MYSQL_PWD 只在当前脚本进程内有效, 不会出现在命令行里
set "MYSQL_PWD=%META_DB_PASSWORD%"
"%MYSQLADMIN%" --protocol=tcp --host=127.0.0.1 --port=%PORT% --user=json_ingestion ping >nul 2>&1
if errorlevel 1 set "PING_FAILED=1"
set "MYSQL_PWD="
if defined PING_FAILED (
  echo [ERROR] 端口 %PORT% 校验失败: 请确认 META_DB_PASSWORD 与首次初始化时一致
  exit /b 1
)
echo MySQL %PORT% 已就绪
exit /b 0
