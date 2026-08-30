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

## 固定商品目录

Demo 每次会创建四个营业店铺和恰好 100 件在售商品：校园文具店 10 件“文具”、校园书店 30 件“图书”、校园生活超市 55 件“生活用品”、校园药店 5 件“药品”。搜索结果每页 20 件，因此全量目录有 5 页，图书有 2 页（20 + 10），生活用品有 3 页（20 + 20 + 15）；文具和药品按精确数量验收。

可用搜索词包括商品名词 `速干`、`数据结构`、`家庭装`、`退热贴`，用途词 `考试准备`、`知识检索`、`宿舍卫生`、`个人日常护理`，以及 SKU 词 `组合装`。`组合装` 应命中每第 5 件商品，共 20 条不重复结果。价格、库存和销量均为固定且有差异的值；首页和搜索结果选择销量降序时，跨前两页仍保持非升序。

## 文件位置

以下路径均相对于仓库根目录：

- 数据库：`vcampus-database/demo/vcampus-shop-auth-demo.accdb`
- 服务端日志：`target/shop-auth-demo/logs/server.log`
- Shop 业务日志：`target/shop-auth-demo/logs/business.log`

日志只记录业务命令、结果和非敏感业务标识；不要向日志或问题报告复制会话凭据或任何真实支付资料。支付页只执行本地模拟支付。

## 人工验收清单

1. 使用固定买家账号登录，确认主窗体显示当前用户，左侧出现“校园商城”。
2. 打开商城首页，确认商品按销量非升序排列；查看前两页，确认跨页顺序仍然正确。
3. 依次选择四个分类，核对文具 10 件、图书 30 件、生活用品 55 件、药品 5 件，并检查图书与生活用品分页数量。
4. 分别搜索 `数据结构`、`宿舍卫生` 和 `组合装`；确认商品名、描述与 SKU 都可命中，且 `组合装` 返回 20 件不重复商品。
5. 进入“黑色速干中性笔”详情，选择标准规格（`demo-stationery-001-sku-1`）SKU，将数量 `2` 加入购物车。
6. 打开购物车，确认商品、单价、数量和合计正确。
7. 进入结算，确认订单后选择支付宝模拟成功。
8. 确认支付结果为 `SUCCEEDED`，没有要求或记录真实支付卡号、账户或验证码。
9. 检查 `target/shop-auth-demo/logs/business.log`，应出现 `SHOP_CHECKOUT` 与 `PAYMENT` 事件。
10. 关闭服务端后检查数据库：买家购物车项为 `0`；本次支付尝试为 `1`；支付状态为 `SUCCEEDED`；`demo-stationery-001-sku-1` 的预留为 `CONSUMED`；库存由 `10` 降至 `8` 且 `reservedQuantity=0`；`demo-stationery-001` 的 `salesCount` 由 `500` 增至 `502`。

自动化 `ShopAuthEndToEndTest` 还会用同一个显式协议 `requestId` 重放支付，并验证库存、预留、销量和支付尝试次数全部保持 exactly once。
