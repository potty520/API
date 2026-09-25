@echo off
chcp 65001 >nul
setlocal EnableExtensions
rem ============================================================
rem  JSON 接入系统 每日备份 (Windows)
rem  内容: 元数据库 + 目标库 + AES 主密钥 data\.secret (三者必须一起备份, 一起恢复)
rem  保留: 最近 14 份, 自动清理更早的
rem  建议: 用任务计划程序每天 02:00 运行本脚本
rem  密码: 只从环境变量 META_DB_PASSWORD / TARGET_DB_PASSWORD 读取, 不写死在脚本里
rem  说明: 通过 MYSQL_PWD 传密码, 避免出现在命令行与进程列表中
rem ============================================================
cd /d "%~dp0"

set "DB_HOST=127.0.0.1"
set "DB_PORT=3306"
set "DB_USER=json_ingestion"
set "META_DB=json_ingestion"
set "TARGET_DB=json_ingestion_target"
set "KEEP=14"
set "BACKUP_ROOT=%~dp0backups"
set "SECRET_FILE=%~dp0data\.secret"
if defined INGESTION_SECRET_FILE set "SECRET_FILE=%INGESTION_SECRET_FILE%"

if not defined META_DB_PASSWORD (
  echo [ERROR] 未设置环境变量 META_DB_PASSWORD, 备份终止
  echo         请先执行: setx META_DB_PASSWORD "你的密码"  然后重开命令行窗口
  exit /b 1
)
if not defined TARGET_DB_PASSWORD set "TARGET_DB_PASSWORD=%META_DB_PASSWORD%"

set "MYSQLDUMP=C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe"
if not exist "%MYSQLDUMP%" set "MYSQLDUMP=mysqldump"
where %MYSQLDUMP% >nul 2>&1
if errorlevel 1 (
  echo [ERROR] 找不到 mysqldump, 请修改脚本顶部的 MYSQLDUMP 路径
  exit /b 1
)

set "STAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
set "STAMP=%STAMP: =0%"
set "DIR=%BACKUP_ROOT%\%STAMP%"
if not exist "%DIR%" mkdir "%DIR%"

set "FAILED=0"

echo [1/3] 备份元数据库 %META_DB% ...
set "MYSQL_PWD=%META_DB_PASSWORD%"
"%MYSQLDUMP%" --host=%DB_HOST% --port=%DB_PORT% --user=%DB_USER% --default-character-set=utf8mb4 --single-transaction --routines --triggers --no-tablespaces %META_DB% > "%DIR%\meta_%META_DB%.sql"
if errorlevel 1 set "FAILED=1"

echo [2/3] 备份目标库 %TARGET_DB% ...
set "MYSQL_PWD=%TARGET_DB_PASSWORD%"
"%MYSQLDUMP%" --host=%DB_HOST% --port=%DB_PORT% --user=%DB_USER% --default-character-set=utf8mb4 --single-transaction --routines --triggers --no-tablespaces %TARGET_DB% > "%DIR%\target_%TARGET_DB%.sql"
if errorlevel 1 set "FAILED=1"
set "MYSQL_PWD="

echo [3/3] 备份 AES 主密钥 ...
if exist "%SECRET_FILE%" (
  copy /y "%SECRET_FILE%" "%DIR%\secret.txt" >nul
  if errorlevel 1 set "FAILED=1"
) else (
  echo [ERROR] 未找到主密钥文件 %SECRET_FILE%
  set "FAILED=1"
)

if not "%FAILED%"=="0" (
  echo [ERROR] 备份过程中出现失败, %DIR% 内容不完整, 请勿用于恢复
  exit /b 1
)

for %%F in ("%DIR%\meta_%META_DB%.sql" "%DIR%\target_%TARGET_DB%.sql") do (
  if %%~zF==0 (
    echo [ERROR] %%~nxF 为空文件, 备份不完整
    exit /b 1
  )
)

rem 只保留最近 %KEEP% 份
for /f "skip=%KEEP% delims=" %%D in ('dir /b /ad /o-n "%BACKUP_ROOT%" 2^>nul') do (
  echo 清理过期备份 %%D
  rd /s /q "%BACKUP_ROOT%\%%D"
)

echo 备份完成: %DIR%
echo 提醒: 请把 backups 目录再同步到异地, 恢复时 sql 与 secret.txt 必须一起使用
exit /b 0
