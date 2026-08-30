# vCampus Shop 认证买家 Demo

此 Demo 使用真实 Socket、用户模块登录、完整 Shop 客户端 UI 和 Access 持久化。请安装 JDK 21 与 Maven，并从 worktree 根目录启动两个 PowerShell 终端。

需要让其他组员拉取分支并通过 Tailscale 或各自本地验收时，请参阅 [Shop 认证买家 Demo 团队测试指南](SHOP_AUTH_DEMO_TEAM_TESTING.md)。

## 启动顺序

终端一先启动组合服务端：

```powershell
Set-Location -LiteralPath "E:\summer-school\vCampus\.worktrees\shop-auth-demo"
.\vcampus-distribution\scripts\start-shop-auth-demo-server.ps1
```

看到 `Shop authenticated buyer demo server started` 后，再在终端二启动客户端：

```powershell
Set-Location -LiteralPath "E:\summer-school\vCampus\.worktrees\shop-auth-demo"
.\vcampus-distribution\scripts\start-shop-auth-demo-client.ps1
```

登录账号：

- 登录标识：`DEMO_BUYER`
- 密码：`DemoPassword7`

Demo 服务端监听本机可用网络接口的 `19090` 端口；客户端默认连接 `127.0.0.1:19090`，也可通过启动参数连接服务端主机的 Tailscale IP。服务端入口会先初始化数据库，再启动只消费该已初始化数据库的运行时。每次重新启动服务端都会恢复固定 Demo 数据。

## 文件位置

以下路径均相对于仓库根目录：

- 数据库：`vcampus-database/demo/vcampus-shop-auth-demo.accdb`
- 服务端日志：`target/shop-auth-demo/logs/server.log`
- Shop 业务日志：`target/shop-auth-demo/logs/business.log`

日志只记录业务命令、结果和非敏感业务标识；不要向日志或问题报告复制会话凭据或任何真实支付资料。支付页只执行本地模拟支付。

## 人工验收清单

1. 使用固定买家账号登录，确认主窗体显示当前用户，左侧出现“校园商城”。
2. 打开商城首页，确认能看到两个营业店铺的在售商品。
3. 进入“签字笔”详情，选择黑色（`demo-pen-black`）SKU，将数量 `2` 加入购物车。
4. 打开购物车，确认商品、单价、数量和合计正确。
5. 进入结算，确认订单后选择支付宝模拟成功。
6. 确认支付结果为 `SUCCEEDED`，没有要求或记录真实支付卡号、账户或验证码。
7. 检查 `target/shop-auth-demo/logs/business.log`，应出现 `SHOP_CHECKOUT` 与 `PAYMENT` 事件。
8. 关闭服务端后检查数据库：买家购物车项为 `0`；本次支付尝试为 `1`；支付状态为 `SUCCEEDED`；`demo-pen-black` 的预留为 `CONSUMED`；库存由 `10` 降至 `8` 且 `reservedQuantity=0`；`demo-pen` 的 `salesCount` 由 `0` 增至 `2`。

自动化 `ShopAuthEndToEndTest` 还会用同一个显式协议 `requestId` 重放支付，并验证库存、预留、销量和支付尝试次数全部保持 exactly once。
