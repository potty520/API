@echo off
setlocal EnableExtensions
cd /d "%~dp0"

call "%CD%\start-mysql.cmd"
if errorlevel 1 exit /b 1

set "JAVA_EXE="
if defined JAVA17_HOME if exist "%JAVA17_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA17_HOME%\bin\java.exe"
for /D %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if exist "%%~fD\bin\java.exe" set "JAVA_EXE=%%~fD\bin\java.exe"
if not defined JAVA_EXE if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
  echo [ERROR] Java 17 was not found. Set JAVA17_HOME and retry.
  exit /b 1
)

"%JAVA_EXE%" -version 2>&1 | findstr /C:"17.0" >nul
if errorlevel 1 (
  echo [ERROR] Java 17 is required: %JAVA_EXE%
  exit /b 1
)

set "JAR=%CD%\backend\target\json-ingestion-system-1.0.0.jar"
if not exist "%JAR%" (
  call "%CD%\build-system.cmd"
  if errorlevel 1 exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r=Invoke-RestMethod 'http://127.0.0.1:3100/api/health' -TimeoutSec 2; if($r.ok){exit 0} } catch {}; exit 1"
if not errorlevel 1 goto open_app

echo Starting Spring Boot on http://127.0.0.1:3100 ...
if not exist "%CD%\logs" mkdir "%CD%\logs"
start "JSON Ingestion System" /min "%JAVA_EXE%" -jar "%JAR%"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ready=$false; 1..45 | ForEach-Object { try { $r=Invoke-RestMethod 'http://127.0.0.1:3100/api/health' -TimeoutSec 2; if($r.ok){$ready=$true; return} } catch {}; Start-Sleep -Seconds 1 }; if(-not $ready){exit 1}"
if errorlevel 1 (
  echo [ERROR] Application startup timed out. See logs\json-ingestion.log.
  exit /b 1
)

:open_app
echo JSON ingestion system is ready: http://127.0.0.1:3100
start "" "http://127.0.0.1:3100"
exit /b 0
