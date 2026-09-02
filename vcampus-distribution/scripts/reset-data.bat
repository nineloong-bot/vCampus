@echo off
setlocal
set "DEMO_DATABASE=%~dp0..\data\course-user-demo.accdb"
echo 确认删除 data/course-user-demo.accdb 并恢复初始 Demo 数据？[y/N]
set "ANSWER="
set /p "ANSWER=> "
if /i not "%ANSWER%"=="y" (
  echo 已取消，Demo 数据未更改。
  exit /b 0
)
if exist "%DEMO_DATABASE%" del /f /q "%DEMO_DATABASE%"
echo 已重置带数据 Demo；下次启动服务端会重新创建。
