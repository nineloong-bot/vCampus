# vCampus 虚拟校园本地测试包

本目录包含统一登录、学籍、课程、图书馆和校园商城。服务端统一监听 `8888`，所有模块共享同一登录会话与 Access 数据库。

## 运行要求

- Windows、macOS 或 Linux；
- JDK 21 或更高版本。

## 直接启动

clone 或 pull 仓库后，进入：

```text
vcampus-distribution/scripts
```

Windows：

1. 双击 `start-server-with-data.bat`。
2. 看到服务端监听 8888 端口后，双击 `start-client.bat`。

macOS / Linux：

1. 运行 `./start-server-with-data.sh`。
2. 服务端启动后运行 `./start-client.sh`。

需要恢复仓库随附的初始数据时，先停止服务端，再运行 `reset-data.bat`。

仓库已随附 `vcampus-distribution/lib/vCampusServer.jar` 和
`vcampus-distribution/lib/vCampusClient.jar`，仅运行演示不需要安装 Maven。

## 从源码重新构建（可选）

需要验证或更新分发 JAR 时，在仓库根目录执行：

```powershell
mvn -pl vcampus-server,vcampus-client -am package
```

打包成功后，Maven 会更新 `vcampus-distribution/lib` 中的两个 JAR。

主要账号（统一初始密码 `123456`）：

- 超级管理员：`ADMIN`
- 学籍 / 选课 / 图书 / 商城 / 用户管理员：`STUDENT` / `COURSE` / `LIBRARY` / `SHOP` / `USER`
- 计算机、数学学院管理员：`CSADMIN` / `MATHADMIN`
- 教师：`T001`～`T024`
- 学生：`213240001`～`213240040`、`213250001`～`213250040`、`213260001`～`213260040`

学生首次登录必须改密。完整账号及逾期场景见 `../vcampus-database/demo/full-test-data/tools/账号清单.md`。

## 数据与日志

- `data/vCampus.accdb` 是全模块演示数据库；
- `logs` 目录包含本机运行日志；
- 不要在 Git 中提交 `logs` 或 `target`；源码更新后应同步更新分发 JAR。

## 停止

先关闭客户端窗口，再回到服务端窗口按 `Ctrl+C` 停止服务。
