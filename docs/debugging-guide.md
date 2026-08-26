# vCampus 本地调试指南

本文说明如何在本机同时运行服务端和客户端，调试登录流程、登录后 Swing 界面、Socket 通信及服务端日志。

## 1. 当前可调试范围

- 服务端监听 `127.0.0.1:8888`，处理 `PING` 和 `USER_LOGIN`。
- 日志页面监听 `127.0.0.1:8889`。
- 客户端已实现连接服务器、登录及登录后主框架。
- 登录后的五个业务模块目前是“功能建设中”页面，尚无真实业务操作。
- 演示账号：`ADMIN`
- 演示密码：`Admin1234`

主要程序入口：

| 程序 | 入口类 |
|---|---|
| 服务端 | `edu.seu.vcampus.server.bootstrap.ServerMain` |
| 客户端 | `edu.seu.vcampus.client.ClientMain` |

## 2. 环境检查

在项目根目录 `D:\_store\vCampus` 打开 PowerShell：

```powershell
java -version
mvn -version
```

项目要求 Java 21 或更高版本。`mvn -version` 中显示的 Java 版本也应为 21。

## 3. 构建与测试

修改代码后，在项目根目录执行：

```powershell
mvn test
mvn package -DskipTests
```

第一条命令运行完整测试；第二条命令生成可运行分发包：

- `vcampus-distribution/lib/vCampusServer.jar`
- `vcampus-distribution/lib/vCampusClient.jar`

只测试客户端及其依赖：

```powershell
mvn -pl vcampus-client -am test
```

只运行登录集成测试：

```powershell
mvn -pl vcampus-client -am "-Dtest=LoginIntegrationTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

只运行登录后 UI 结构测试：

```powershell
mvn -pl vcampus-client -am "-Dtest=MainFrameLayoutTest,UiThemeTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## 4. 最快的本地联调方式

### 4.1 启动服务端

双击：

```text
vcampus-distribution\scripts\start-server.bat
```

也可以在 PowerShell 中运行：

```powershell
cd D:\_store\vCampus\vcampus-distribution
.\scripts\start-server.bat
```

终端应持续保留并输出所有类别的最新日志。看到以下信息表示启动成功：

```text
VCampus 服务端已启动，监听端口 8888
VCampus 日志页面：http://127.0.0.1:8889/
```

不要关闭这个终端。

### 4.2 查看服务端日志

浏览器打开：

```text
http://127.0.0.1:8889/
```

页面仅允许本机访问，每 3 秒刷新一次，每类日志显示最近 200 行：

| 文件 | 内容 |
|---|---|
| `server.log` | 启动、连接、请求完成和异常 |
| `security.log` | 登录成功、登录拒绝等安全事件 |
| `database.log` | 数据库初始化和数据库相关事件 |
| `business.log` | 业务操作日志 |

原始日志文件位于 `vcampus-distribution/logs`。

### 4.3 启动客户端

确认服务端已经启动后，双击：

```text
vcampus-distribution\scripts\start-client.bat
```

或另开一个 PowerShell：

```powershell
cd D:\_store\vCampus\vcampus-distribution
.\scripts\start-client.bat
```

使用 `ADMIN / Admin1234` 登录。登录成功后会进入 1280 × 800 的主框架，默认打开“学籍档案”。点击左侧导航可检查五个页面的切换。

## 5. IDE 断点调试

推荐分别建立“服务端”和“客户端”两个 Java Application 运行配置。无论使用 IntelliJ IDEA、Eclipse 还是 VS Code，关键参数相同。

### 5.1 服务端配置

| 配置项 | 值 |
|---|---|
| Main class | `edu.seu.vcampus.server.bootstrap.ServerMain` |
| Module/classpath | `vcampus-server` |
| Working directory | `D:\_store\vCampus\vcampus-distribution` |
| Program arguments | `config/server.properties` |
| VM options | `-Dlogback.configurationFile=config/logback.xml` |
| JRE | Java 21 |

先以 Debug 模式启动服务端。建议断点：

- `ServerMain.run`：检查配置、数据库和路由注册。
- `SocketServer.handle`：查看客户端请求和服务端响应。
- `MessageRouter.route`：查看命令如何分发。
- `LoginService.login`：检查登录校验和返回结果。
- `AccessDatabase.initialize`：检查数据库初始化。

### 5.2 客户端配置

| 配置项 | 值 |
|---|---|
| Main class | `edu.seu.vcampus.client.ClientMain` |
| Module/classpath | `vcampus-client` |
| Working directory | `D:\_store\vCampus\vcampus-distribution` |
| Program arguments | `config/client.properties` |
| VM options | `-Dlogback.configurationFile=config/logback.xml` |
| JRE | Java 21 |

服务端启动后，再以 Debug 模式启动客户端。建议断点：

- `ClientConnection.connect`：检查 Socket 建立过程。
- `LoginFrame.submit`：查看 UI 提交的账号信息。
- `UserClient.login`：查看 `USER_LOGIN` 请求构造。
- `ClientConnection.send`：检查请求 ID、会话令牌和超时。
- `ClientConnection.readResponses`：查看异步响应接收。
- `LoginFrame.complete`：检查登录结果及主窗口创建。
- `MainFrame.displayPage`：检查登录后导航切换。

Swing 组件应只在事件分发线程（EDT）上更新。调试异步登录时，可分别在线程池回调和 `SwingUtilities.invokeLater` 内设置断点，避免把“后台请求完成”和“界面完成更新”误认为同一个时刻。

## 6. 配置与数据位置

| 文件 | 用途 |
|---|---|
| `vcampus-distribution/config/server.properties` | 服务端端口、线程数、数据库和会话配置 |
| `vcampus-distribution/config/client.properties` | 服务端地址及客户端超时配置 |
| `vcampus-distribution/config/logback.xml` | 控制台及分类日志配置 |
| `vcampus-distribution/data/vCampus.accdb` | 本地 Access 数据库 |

这些路径依赖正确的工作目录。直接从 IDE 启动但未设置 `vcampus-distribution` 为工作目录时，常见现象是找不到配置文件、数据库写到了错误位置，或日志目录出现在其他位置。

修改端口时，必须同时修改：

- 服务端 `server.properties` 中的 `server.port`；
- 客户端 `client.properties` 中的 `server.port`。

`server.logViewerPort` 必须与 `server.port` 不同。

## 7. 常见问题

### 服务端终端立即关闭

不要直接运行 JAR；优先使用 `start-server.bat`。该脚本会保留终端，并在失败时显示退出码和错误原因。

### 提示端口已被占用

先查找监听 8888 或 8889 的进程：

```powershell
Get-NetTCPConnection -State Listen |
  Where-Object { $_.LocalPort -in 8888, 8889 } |
  Select-Object LocalAddress, LocalPort, OwningProcess
```

确认 PID 对应的是旧 vCampus 服务端后再停止：

```powershell
Get-Process -Id <PID>
Stop-Process -Id <PID>
```

不要使用 `taskkill /IM java.exe`，它会关闭电脑上所有 Java 程序。

### 客户端提示网络异常或启动失败

依次检查：

1. 服务端终端是否仍在运行；
2. `http://127.0.0.1:8889/` 是否可打开；
3. 客户端与服务端配置是否都使用端口 8888；
4. 防火墙或安全软件是否阻止了本机 Java 进程；
5. 客户端启动终端中是否有“客户端启动失败”信息。

### 登录失败

确认账号为 `ADMIN`、密码为 `Admin1234`，然后查看 `security.log`。密码不会写入日志。

### 数据库异常

先查看 `database.log`，确认实际数据库路径。不要直接删除 `vCampus.accdb`；如需验证全新数据库初始化，应先停止服务端并备份数据库文件。

### 中文日志在测试终端中乱码

脚本已执行 `chcp 65001`。如果 IDE 控制台仍乱码，将 IDE 控制台和项目编码设置为 UTF-8，再重新启动调试进程。

## 8. 停止程序

- 客户端：关闭 Swing 窗口。
- 服务端：在运行 `start-server.bat` 的终端按 `Ctrl+C`。
- 停止后访问 8889 应失败，8888 和 8889 不应继续处于监听状态。

可用以下命令确认：

```powershell
Get-NetTCPConnection -State Listen |
  Where-Object { $_.LocalPort -in 8888, 8889 }
```

## 9. 推荐的日常调试顺序

1. 修改代码。
2. 运行相关单元测试或集成测试。
3. 停止旧客户端和旧服务端。
4. 执行 `mvn package -DskipTests` 更新分发包。
5. 启动服务端并确认 8889 日志页面可用。
6. 启动客户端，执行一次登录和目标操作。
7. 同时观察客户端断点、服务端断点以及 8889 日志。
8. 最后运行 `mvn test` 做完整回归。
