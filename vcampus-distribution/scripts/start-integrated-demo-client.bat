@echo off
setlocal
cd /d "%~dp0.."
java -Dlogback.configurationFile=config/logback.xml -jar lib/vCampusClient.jar config/client.properties
