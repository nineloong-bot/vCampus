@echo off
setlocal
cd /d "%~dp0.."
java -Dlogback.configurationFile=config/logback.xml -cp lib/vCampusServer.jar edu.seu.vcampus.server.bootstrap.demo.IntegratedDemoServerMain config/integrated-demo-server.properties
