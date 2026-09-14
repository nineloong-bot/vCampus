@echo off
setlocal
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_RAW=%%~v"
for /f "tokens=1 delims=." %%m in ("%JAVA_RAW%") do set "JAVA_MAJOR=%%m"
if not defined JAVA_MAJOR echo 未找到 Java，请安装 Java 21 或更高版本。 & exit /b 1
if %JAVA_MAJOR% LSS 21 echo 需要 Java 21 或更高版本。 & exit /b 1
cd /d "%~dp0.."
java -Dlogback.configurationFile=config\logback.xml -jar lib\vCampusClient.jar config\client.properties
