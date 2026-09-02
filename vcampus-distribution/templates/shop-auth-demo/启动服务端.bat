@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java was not found. Install Java 21 and add java to PATH.
    goto :failed
)

set "JAVA_RAW="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_RAW=%%~v"
for /f "tokens=1 delims=." %%m in ("%JAVA_RAW%") do set "JAVA_MAJOR=%%m"
if not defined JAVA_MAJOR (
    echo [ERROR] Cannot detect the Java version. Java 21 or newer is required.
    goto :failed
)
if %JAVA_MAJOR% LSS 21 (
    echo [ERROR] Java %JAVA_RAW% is too old. Java 21 or newer is required.
    goto :failed
)

echo Starting the vCampus Shop Demo server on port 8888...
echo Keep this window open, then double-click the client BAT.
java -Dlogback.configurationFile=config\logback.xml -cp lib\vCampusServer.jar edu.seu.vcampus.server.shop.demo.ShopAuthDemoServerMain database\vcampus-shop-auth-demo.accdb 8888 database\schema database\seed
if errorlevel 1 (
    echo [ERROR] Server startup failed. Check port 8888 and the logs directory.
    goto :failed
)
goto :eof

:failed
if not defined VCAMPUS_SHOP_DEMO_NO_PAUSE pause
exit /b 1
