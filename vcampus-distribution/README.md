# vCampus 本地演示分发目录

本目录用于 vCampus 课程项目的本地演示，不得用于真实生产环境。

## 运行要求

- Windows 系统；
- JDK 21 或更高版本；
- 无需单独安装 Git 以外的运行依赖，但首次构建需要 Maven。

## 构建

clone 仓库后，在仓库根目录执行：

```powershell
mvn -pl vcampus-server,vcampus-client -am package
```

打包成功后，Maven 会在 `vcampus-distribution/lib` 中生成最新的
`vCampusServer.jar` 和 `vCampusClient.jar`。这些 JAR 是构建产物，不提交到 Git。

## 启动

进入：

```text
vcampus-distribution/scripts
```

1. 双击 `start-server.bat`。
2. 看到服务端监听 8888 端口后，双击 `start-client.bat`。

演示账号：`DEMO_ADMIN`

演示密码：`admin123456`

该密码仅用于课程 demo，不得用于真实环境或真实账户。

## 数据与日志

- `data/vCampus.accdb` 是课程演示数据库；
- `logs` 目录包含本机运行日志；
- 不要在 Git 中提交 `logs`、`target` 或 Maven 构建生成的 JAR。

## 停止

先关闭客户端窗口，再回到服务端窗口按 `Ctrl+C` 停止服务。
