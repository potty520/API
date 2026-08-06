@echo off
setlocal

for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:"127.0.0.1:3100 .*LISTENING"') do taskkill /PID %%P /T /F >nul 2>&1
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:"127.0.0.1:3307 .*LISTENING"') do taskkill /PID %%P /T /F >nul 2>&1

echo JSON ingestion application and dedicated MySQL have been stopped.
