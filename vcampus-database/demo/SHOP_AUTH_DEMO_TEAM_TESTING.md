# Shop 认证买家 Demo 团队测试指南

本文用于组员获取 `demo/shop-auth` 分支并完成人工验收。测试有两种模式：

- **模式 A：完整本地测试。** 每位测试者都在自己的电脑上运行服务端和客户端，数据与其他测试者隔离。
- **模式 B：Tailscale 联机测试。** 一人运行服务端，其他组员通过同一 Tailscale 内网只运行客户端，共同使用服务端主机上的 Demo 数据库。

## 前置环境

所有测试者需要：

- Windows PowerShell
- JDK 21
- Maven
- Git

使用模式 B 时，服务端和客户端设备还必须加入同一个 tailnet、保持 Tailscale 在线，并受该 tailnet 的 ACL 或管理员策略允许互访。

可先检查基础工具：

```powershell
java -version
mvn -version
git --version
```

## 获取或更新分支

以下命令在本地仓库中运行。

首次创建本地分支：

```powershell
git fetch origin
git switch --track origin/demo/shop-auth
```

已经有本地 `demo/shop-auth` 分支：

```powershell
git switch demo/shop-auth
git pull --ff-only
```

本指南对应 `demo/shop-auth`。请勿使用 `feat/shop-only` 进行本测试；该分支是纯 Shop 正式分支，不包含组合登录 Demo。

需要记录当前测试提交时运行：

```powershell
git rev-parse HEAD
```

## 固定测试账号

- 登录标识：`DEMO_BUYER`
- 密码：`DemoPassword7`

该账号只用于本地 Demo 数据。反馈测试结果时不要粘贴密码或会话信息。

## 模式 A：每人完整本地测试

在仓库根目录打开两个 PowerShell 窗口，并先启动服务端。

服务端窗口：

```powershell
Set-Location -LiteralPath '<仓库根目录>'
.\vcampus-distribution\scripts\start-shop-auth-demo-server.ps1
```

看到以下启动消息后再启动客户端：

```text
Shop authenticated buyer demo server started
```

客户端窗口：

```powershell
Set-Location -LiteralPath '<仓库根目录>'
.\vcampus-distribution\scripts\start-shop-auth-demo-client.ps1
```

客户端默认连接 `127.0.0.1:19090`。

## 模式 B：通过 Tailscale 联机测试

### 服务端主机

在仓库根目录启动服务端：

```powershell
Set-Location -LiteralPath '<仓库根目录>'
.\vcampus-distribution\scripts\start-shop-auth-demo-server.ps1
```

看到 `Shop authenticated buyer demo server started` 后，获取该主机的 Tailscale IPv4 地址：

```powershell
tailscale ip -4
```

把输出的地址作为 `<服务器的 Tailscale IP>` 发给组员。服务端监听本机可用网络接口的 `19090` 端口；Tailscale 联机不需要公网端口映射，也不需要 Funnel。

### 客户端主机

先确认 Tailscale 能到达服务端。在 Windows PowerShell 中运行：

```powershell
tailscale ping <服务器的 Tailscale IP>
Test-NetConnection <服务器的 Tailscale IP> -Port 19090
```

其中 `Test-NetConnection` 的 `TcpTestSucceeded` 应为 `True`。在 macOS 或 Linux 上可用以下命令检测端口：

```bash
nc -vz <服务器的 Tailscale IP> 19090
```

然后在 Windows 客户端的仓库根目录运行：

```powershell
Set-Location -LiteralPath '<仓库根目录>'
.\vcampus-distribution\scripts\start-shop-auth-demo-client.ps1 -ServerHost '<服务器的 Tailscale IP>' -ServerPort 19090
```

## 人工验收流程

1. 使用固定买家账号登录，确认主窗体显示当前用户，左侧出现“校园商城”。
2. 打开商城首页，确认能看到两个营业店铺的在售商品。
3. 进入“黑色签字笔”详情，选择黑色 SKU，将数量 `2` 加入购物车。
4. 打开购物车，确认商品、单价、数量和合计正确；也可调整数量并确认页面同步更新。
5. 进入结算，确认订单后选择支付宝模拟成功。
6. 确认支付结果为 `SUCCEEDED`，页面没有要求真实卡号、账户或验证码。
7. 检查购物车已经清空，并核对 `target/shop-auth-demo/logs/business.log` 中出现 `SHOP_CHECKOUT` 与 `PAYMENT` 事件。
8. 客户端和服务端都退出后检查数据库：买家购物车项为 `0`；本次支付尝试为 `1`；支付状态为 `SUCCEEDED`；`demo-pen-black` 的预留为 `CONSUMED`；库存由 `10` 降至 `8` 且 `reservedQuantity=0`；`demo-pen` 的 `salesCount` 由 `0` 增至 `2`。

## 数据库与日志位置

以下路径均相对于仓库根目录：

- Demo 数据库：`vcampus-database/demo/vcampus-shop-auth-demo.accdb`
- 服务端运行日志：`target/shop-auth-demo/logs/server.log`
- Shop 业务日志：`target/shop-auth-demo/logs/business.log`

每次重新启动服务端都会重建固定 Demo 数据，因此新一轮验收会从预设账号、商品、库存和购物车状态开始。

## 联机协作约定

模式 B 的所有远程测试者共用服务端主机上的同一个 Demo 数据库和固定账号。为避免购物车、库存及支付状态互相影响，建议组员串行测试：一人完整跑完流程并记录结果后，再交给下一人。

重新启动服务端前，请先让所有客户端退出。日志和问题反馈中只保留必要的业务标识；不要复制密码、会话信息或任何真实支付信息。

## 常见问题

### 无法连接服务端

在两端运行 `tailscale status`，确认设备在线且位于同一 tailnet；再检查 tailnet ACL 或管理员策略是否允许互访、服务端进程是否仍在运行，以及服务端主机的防火墙是否允许 Tailscale 网络访问 TCP `19090`。

### 端口检测成功，但客户端仍连接失败

重新核对客户端的 `-ServerHost`、`-ServerPort 19090` 和 Tailscale IP，确认没有误用本机 `127.0.0.1`，并确认服务端窗口没有退出或报错。

### 数据库提示被占用

先退出所有客户端，再停止服务端，确认没有 Demo Java 进程继续占用数据库，然后重新启动服务端。

### 构建失败

运行以下命令，确认 JDK 主版本为 21，且 Maven 使用的是预期 Java 环境：

```powershell
java -version
mvn -version
```

## 结束测试与反馈模板

测试完成后先退出客户端，再由服务端主机停止服务端。反馈时可复制以下模板：

```text
测试提交：<git rev-parse HEAD 的输出>
测试模式：<A 本地 / B Tailscale>
操作系统：<系统名称和版本>
测试步骤：<执行到的步骤>
期望结果：<期望行为>
实际结果：<实际行为>
业务日志：requestId=<值>，command=<值>，code=<值>
补充说明：<截图、时间点或复现频率；不要粘贴凭据>
```

`requestId`、`command` 和 `code` 可从 `target/shop-auth-demo/logs/business.log` 中定位。反馈中不要粘贴账号密码、会话信息或真实支付资料。
