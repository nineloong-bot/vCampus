@echo off
setlocal
cd /d "%~dp0\.."
set "TOKEN=%~1"
if "%TOKEN%"=="" set "TOKEN=student-demo-1"
set "ROLE=%~2"
if "%ROLE%"=="" set "ROLE=STUDENT"
java -Dlogback.configurationFile=config/logback.xml -cp lib\vCampusClient.jar edu.seu.vcampus.client.course.demo.CourseDemoClientMain config\client.properties "%TOKEN%" "%ROLE%"
