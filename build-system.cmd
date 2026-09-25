@echo off
chcp 65001 >nul
setlocal EnableExtensions
rem ============================================================
rem  构建 JSON 接入系统
rem  1) Vue 前端 -> 产物直接输出到 backend/src/main/resources/static
rem  2) Spring Boot 后端 -> backend/target/json-ingestion-system-1.0.0.jar (含单元测试)
rem  依赖: JDK 17(建议设 JAVA17_HOME)、Node.js 18+、Maven 3.9+
rem ============================================================
cd /d "%~dp0"

set "USER_JAVA_HOME=%JAVA_HOME%"
set "JAVA_HOME="
if defined JAVA17_HOME if exist "%JAVA17_HOME%\bin\javac.exe" set "JAVA_HOME=%JAVA17_HOME%"
if not defined JAVA_HOME for /D %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if exist "%%~fD\bin\javac.exe" set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME if defined USER_JAVA_HOME if exist "%USER_JAVA_HOME%\bin\javac.exe" set "JAVA_HOME=%USER_JAVA_HOME%"
if not defined JAVA_HOME (
  echo [ERROR] 未找到 Java 17 JDK, 请设置环境变量 JAVA17_HOME 后重试
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

where npm.cmd >nul 2>&1
if errorlevel 1 (
  echo [ERROR] 未找到 npm, 请先安装 Node.js 18 或以上版本
  exit /b 1
)
where mvn.cmd >nul 2>&1
if errorlevel 1 (
  echo [ERROR] 未找到 mvn, 请先安装 Maven 3.9 或以上版本
  exit /b 1
)

echo [1/2] 构建前端 ...
rem 有 lock 文件时用 npm ci, 保证依赖版本与锁文件一致(装过就跳过 install 会导致依赖过期)
if exist "%CD%\frontend\package-lock.json" (
  call npm.cmd ci --prefix "%CD%\frontend" --cache "%CD%\.cache\npm"
) else (
  call npm.cmd install --prefix "%CD%\frontend" --cache "%CD%\.cache\npm"
)
if errorlevel 1 exit /b 1
call npm.cmd run build --prefix "%CD%\frontend"
if errorlevel 1 exit /b 1

echo [2/2] 构建后端 JAR (会执行单元测试) ...
set "MAVEN_SKIP_RC=true"
set "MAVEN_OPTS=-Dmaven.repo.local=%CD%\.cache\m2"
call mvn.cmd -B -ntp -f "%CD%\backend\pom.xml" clean package
if errorlevel 1 exit /b 1

set "JAR=%CD%\backend\target\json-ingestion-system-1.0.0.jar"
if not exist "%JAR%" (
  echo [ERROR] 构建结束但未找到 %JAR%
  exit /b 1
)
echo 构建完成: backend\target\json-ingestion-system-1.0.0.jar
exit /b 0
