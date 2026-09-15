@echo off
setlocal EnableExtensions
cd /d "%~dp0.." || goto :failed

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java was not found. Install Java 21 or newer and add it to PATH.
  goto :failed
)

set "JAVA_RAW="
set "JAVA_MAJOR="
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

if not exist "lib\vCampusServer.jar" (
  echo [ERROR] Missing lib\vCampusServer.jar. Rebuild or extract the complete package.
  goto :failed
)
if not exist "config\server-with-data.properties" (
  echo [ERROR] Missing config\server-with-data.properties.
  goto :failed
)

echo Starting the vCampus server using config\server-with-data.properties...
echo Keep this window open while testing.
java -Dlogback.configurationFile=config\logback.xml -jar lib\vCampusServer.jar config\server-with-data.properties
if errorlevel 1 (
  echo [ERROR] Server startup failed. Check the error above, port usage, and logs.
  goto :failed
)
exit /b 0

:failed
if not defined VCAMPUS_DISTRIBUTION_NO_PAUSE pause
exit /b 1
