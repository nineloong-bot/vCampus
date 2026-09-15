@echo off
setlocal
set "DEMO_DATABASE=%~dp0..\data\vCampus.accdb"
echo 确认删除 data/vCampus.accdb 并恢复虚拟校园测试数据？[y/N]
set "ANSWER="
set /p "ANSWER=> "
if /i not "%ANSWER%"=="y" (
  echo 已取消，Demo 数据未更改。
  exit /b 0
)
if exist "%DEMO_DATABASE%" del /f /q "%DEMO_DATABASE%"
echo 已重置虚拟校园数据；下次启动服务端会重新创建。
