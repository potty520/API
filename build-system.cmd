@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "JAVA_HOME="
if defined JAVA17_HOME if exist "%JAVA17_HOME%\bin\javac.exe" set "JAVA_HOME=%JAVA17_HOME%"
for /D %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if exist "%%~fD\bin\javac.exe" set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME (
  echo [ERROR] Java 17 JDK was not found. Set JAVA17_HOME and retry.
  exit /b 1
)

echo Building Vue frontend...
if not exist "%CD%\frontend\node_modules" call npm.cmd install --prefix "%CD%\frontend" --cache "%CD%\.cache\npm"
if errorlevel 1 exit /b 1
call npm.cmd run build --prefix "%CD%\frontend"
if errorlevel 1 exit /b 1

echo Building Spring Boot executable JAR...
set "MAVEN_SKIP_RC=true"
set "MAVEN_OPTS=-Duser.home=%CD%\.cache -Dmaven.repo.local=%CD%\.cache\m2"
call mvn.cmd -f "%CD%\backend\pom.xml" clean package
if errorlevel 1 exit /b 1

echo Build completed: backend\target\json-ingestion-system-1.0.0.jar
