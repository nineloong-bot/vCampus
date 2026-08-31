@echo off
setlocal
set "DEMO_DATABASE=%~dp0..\data\course-user-demo.accdb"
if exist "%DEMO_DATABASE%" del /f /q "%DEMO_DATABASE%"
echo 已重置带数据 Demo；下次启动服务端会重新创建。
