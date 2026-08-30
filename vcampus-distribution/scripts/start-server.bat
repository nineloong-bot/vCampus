@echo off
setlocal
title vCampus Server - close this window to stop port 8888
java -version >nul 2>&1
if errorlevel 1 goto no_java
cd /d "%~dp0.."
echo Starting vCampus server. Close this window to stop the server and release port 8888.
java -Dlogback.configurationFile=config\logback.xml -jar lib\vCampusServer.jar config\server.properties
if errorlevel 1 goto failed
exit /b 0

:no_java
echo Java was not found. Install Java 21 or newer.
pause
exit /b 1

:failed
echo.
echo Server startup failed. If port 8888 is occupied, close the old server window first.
pause
exit /b 1
