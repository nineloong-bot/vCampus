# vCampus 本地演示分发目录

本目录用于 vCampus 课程项目的本地演示，不得用于真实生产环境。

## 运行要求

- Windows 系统；
- JDK 21 或更高版本。

## 直接启动

clone 或 pull 仓库后，进入：

```text
vcampus-distribution/scripts
```

1. 先运行 `start-server-with-data.bat`。
2. 看到服务端监听 8888 端口后，再运行 `start-client.bat`。

仓库已随附 `vcampus-distribution/lib/vCampusServer.jar` 和
`vcampus-distribution/lib/vCampusClient.jar`，仅运行演示不需要安装 Maven。

## 从源码重新构建（可选）

需要验证或更新分发 JAR 时，在仓库根目录执行（要求 Java 21）：

```powershell
mvn -pl vcampus-server,vcampus-client -am package
```

打包成功后，Maven 会更新 `vcampus-distribution/lib` 中的两个 JAR。

当前快速 Demo 账号为 `DEMO_STUDENT`、`DEMO_TEACHER`、`DEMO_ADMIN`。

三个账号的初始密码均为 `DemoPassword7`；`DEMO_ADMIN` 首次登录必须改密，改密后返回登录页并用新密码重新登录。

该密码仅用于课程 demo，不得用于真实环境或真实账户。

## 数据与日志

- `data/vCampus.accdb` 是课程演示数据库；
- 带数据 Demo 使用 `data/course-user-demo.accdb`，只由 `start-server-with-data` 初始化；
- `reset-data.sh` / `reset-data.bat` 只在确认后删除 `data/course-user-demo.accdb`，下次启动服务端恢复初始数据；
- `logs` 目录包含本机运行日志；
- 不要在 Git 中提交 `logs` 或 `target`；源码更新后应同步更新分发 JAR。

## 停止

先关闭客户端窗口，再回到服务端窗口按 `Ctrl+C` 停止服务。
