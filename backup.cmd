@echo off
setlocal EnableExtensions
rem ============================================================
rem  JSON ???? ??????(Windows)
rem  ??: ???? json_ingestion + ??? json_ingestion_target + AES ??? data/.secret
rem  ??: ?? 14 ???, ???????
rem  ??: ????????????, ???? 02:00
rem  ??: ???? DB_PASS ???????(????????????)
rem ============================================================
cd /d "%~dp0"

set "MYSQLDUMP=C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe"
if not exist "%MYSQLDUMP%" set "MYSQLDUMP=mysqldump"

set "DB_HOST=127.0.0.1"
set "DB_PORT=3306"
set "DB_USER=json_ingestion"
set "DB_PASS=${META_DB_PASSWORD}"
set "META_DB=json_ingestion"
set "TARGET_DB=json_ingestion_target"

set "BACKUP_ROOT=%~dp0backups"
set "STAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
set "STAMP=%STAMP: =0%"
set "DIR=%BACKUP_ROOT%\%STAMP%"
set "KEEP=14"

if not exist "%DIR%" mkdir "%DIR%"

echo [1/3] ?????? %META_DB% ...
"%MYSQLDUMP%" --host=%DB_HOST% --port=%DB_PORT% --user=%DB_USER% --password=%DB_PASS% --default-character-set=utf8mb4 --single-transaction --routines --triggers --no-tablespaces %META_DB% > "%DIR%\meta_%META_DB%.sql" 2> "%DIR%\dump_meta.err"
if errorlevel 1 ( echo   [ERROR] ????????, ?? dump_meta.err & exit /b 1 )

echo [2/3] ????? %TARGET_DB% ...
"%MYSQLDUMP%" --host=%DB_HOST% --port=%DB_PORT% --user=%DB_USER% --password=%DB_PASS% --default-character-set=utf8mb4 --single-transaction --routines --triggers --no-tablespaces %TARGET_DB% > "%DIR%\data_%TARGET_DB%.sql" 2> "%DIR%\dump_data.err"
if errorlevel 1 ( echo   [ERROR] ???????, ?? dump_data.err & exit /b 1 )

echo [3/3] ?? AES ??? ...
if exist "data\.secret" copy /y "data\.secret" "%DIR%\secret.txt" >nul
if not exist "%DIR%\secret.txt" ( echo   [ERROR] data\.secret ???????? & exit /b 1 )

echo.
echo ????: %DIR%
dir /b "%DIR%"

rem ---- ???? KEEP ????? ----
for /f "skip=%KEEP% delims=" %%D in ('dir /b /ad /o-d "%BACKUP_ROOT%" 2^>nul') do (
  echo ?????: %BACKUP_ROOT%\%%D
  rmdir /s /q "%BACKUP_ROOT%\%%D"
)
echo ???
exit /b 0

