@echo off
setlocal
chcp 65001 >nul
title vCampus Server

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_RAW=%%~v"
for /f "tokens=1 delims=." %%m in ("%JAVA_RAW%") do set "JAVA_MAJOR=%%m"
if not defined JAVA_MAJOR goto :missing_java
if %JAVA_MAJOR% LSS 21 goto :old_java

cd /d "%~dp0.."
echo Starting vCampus server...
echo Client endpoint: 127.0.0.1:8888
echo Log viewer: http://127.0.0.1:8889/
echo Press Ctrl+C to stop the server.
echo.
java -Dlogback.configurationFile=config\logback.xml -jar lib\vCampusServer.jar config\server.properties

set "SERVER_EXIT_CODE=%ERRORLEVEL%"
echo.
if "%SERVER_EXIT_CODE%"=="0" (
    echo vCampus server stopped.
) else (
    echo vCampus server failed with exit code %SERVER_EXIT_CODE%.
    echo Check the error above. Ports 8888 or 8889 may already be in use.
)
echo Log directory: %CD%\logs
pause
exit /b %SERVER_EXIT_CODE%

:missing_java
echo Java was not found. Install Java 21 or later.
pause
exit /b 1

:old_java
echo Java 21 or later is required.
pause
exit /b 1
