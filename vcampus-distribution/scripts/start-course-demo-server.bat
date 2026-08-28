@echo off
setlocal
cd /d "%~dp0\.."
java -Dlogback.configurationFile=config/logback.xml -cp lib\vCampusServer.jar edu.seu.vcampus.server.course.demo.CourseDemoServerMain config\course-demo.properties
