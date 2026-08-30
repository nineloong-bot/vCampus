@echo off
setlocal
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_RAW=%%~v"
for /f "tokens=1 delims=." %%m in ("%JAVA_RAW%") do set "JAVA_MAJOR=%%m"
if not defined JAVA_MAJOR echo 未找到 Java，请安装 Java 21 或更高版本。 & exit /b 1
if %JAVA_MAJOR% LSS 21 echo 需要 Java 21 或更高版本。 & exit /b 1
cd /d "%~dp0.."
title vCampus Server - close this window to stop port 8888
echo 正在启动 vCampus 服务端。关闭此窗口将停止服务端并释放端口。
java -Dlogback.configurationFile=config\logback.xml -jar lib\vCampusServer.jar config\server.properties
if errorlevel 1 (
    echo.
    echo 服务端启动失败。请查看上方错误；若端口已占用，请先关闭旧服务端窗口。
    pause
)
