@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.." || goto :failed

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] 未找到 Java，请安装 Java 21 或更高版本并加入 PATH。
  goto :failed
)

set "JAVA_RAW="
set "JAVA_MAJOR="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_RAW=%%~v"
for /f "tokens=1 delims=." %%m in ("%JAVA_RAW%") do set "JAVA_MAJOR=%%m"
if not defined JAVA_MAJOR (
  echo [ERROR] 无法识别 Java 版本，需要 Java 21 或更高版本。
  goto :failed
)
if %JAVA_MAJOR% LSS 21 (
  echo [ERROR] 当前 Java 版本为 %JAVA_RAW%，需要 Java 21 或更高版本。
  goto :failed
)

if not exist "lib\vCampusServer.jar" (
  echo [ERROR] 缺少 lib\vCampusServer.jar，请重新构建或解压完整发布包。
  goto :failed
)
if not exist "config\server-with-data.properties" (
  echo [ERROR] 缺少 config\server-with-data.properties。
  goto :failed
)

echo 正在启动 vCampus 服务端，端口配置见 config\server-with-data.properties...
echo 请保持此窗口打开。
java -Dlogback.configurationFile=config\logback.xml -jar lib\vCampusServer.jar config\server-with-data.properties
if errorlevel 1 (
  echo [ERROR] 服务端启动失败，请检查上方错误、端口占用和 logs 目录。
  goto :failed
)
exit /b 0

:failed
if not defined VCAMPUS_DISTRIBUTION_NO_PAUSE pause
exit /b 1
