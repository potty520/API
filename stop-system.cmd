@echo off
chcp 65001 >nul
setlocal EnableExtensions
rem 停止 JSON 接入系统应用(3100)与本项目专用 MySQL(3307)
cd /d "%~dp0"

set "FOUND=0"
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":3100 .*LISTENING"') do (
  echo 停止应用进程 PID=%%P
  taskkill /PID %%P /T /F >nul 2>&1
  set "FOUND=1"
)
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":3307 .*LISTENING"') do (
  echo 停止 MySQL 进程 PID=%%P
  taskkill /PID %%P /T /F >nul 2>&1
  set "FOUND=1"
)
if "%FOUND%"=="0" echo 未发现正在监听 3100 或 3307 的进程
echo JSON 接入系统已停止
exit /b 0
