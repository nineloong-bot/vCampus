# vCampus Shop 登录购物 Demo 设计

## 1. 目标

本阶段基于真实 vCampus Socket 协议和用户会话契约，交付可运行的买家购物 Demo。用户登录后可以浏览商城、选择 SKU、维护持久化购物车、结算并完成模拟支付，最后查看支付结果。

本阶段对应 Task 7 的买家购物主链路。该链路稳定后，再分别完成 Task 6 的订单履约，以及 Task 7 的店主和管理员页面。

## 2. 范围与验收流程

人工验收流程为：

1. 启动 Demo 服务端并自动生成本地 Access 数据库。
2. 启动 Demo 客户端，使用 `DEMO_BUYER` 和 `DemoPassword7` 登录。
3. 打开商城首页，浏览或搜索预置商品。
4. 打开商品详情，选择可用 SKU 并加入购物车。
5. 修改或删除购物车商品，检查重新计算的总金额。
6. 结算选中商品，创建订单组并预留库存。
7. 选择模拟支付渠道，执行支付成功、失败或重试。
8. 显示支付单号、子订单数量、金额、渠道和最终状态。
9. 验证购物车已清空、库存只扣减一次，并可在数据库中查看订单和支付尝试。

首批客户端界面包括商城首页、商品搜索、商品详情、购物车、结算、模拟收银台和支付结果。买家店铺主页复用相同的商品卡片组件，并使用店铺范围查询。

## 3. 架构

请求链路为：

```text
LoginFrame
  -> USER_LOGIN
  -> ClientConnection 在内存保存 sessionToken
  -> ShopClientService 发送 SHOP_* 请求
  -> BuyerShopHandlers
  -> ShopUserPort 解析已登录身份
  -> 现有商品、购物车、结算和支付服务
  -> 本地 Access 数据库
```

Shop 模块通过已发布接口使用现有 Socket、路由、异步客户端、事务、资源锁、导航、日志和鉴权契约。Shop 自己提供适配器和组合启动入口，使功能可以独立开发与测试。

### 3.1 Shop 功能分支

`feat/shop-only` 负责：

- `vcampus-server/.../shop/handler/BuyerShopHandlers`
- `vcampus-client/.../shop/service/ShopClientService`
- `vcampus-client/.../shop/ui` 下的路由、页面、对话框和 Shop 组合组件
- `vcampus-server/.../shop/logging` 下的业务事件日志
- Handler、客户端、导航、UI、隐私和业务日志测试

Handler 依赖现有 `ShopUserPort`，业务行为继续由已经实现的 Shop Service 承担。客户端通过现有 `MainFrame` 内容区和导航区扩展点安装 Shop UI。

### 3.2 Demo 集成分支

`demo/shop-auth` 是独立集成分支，以当前用户模块分支为基础并引入已评审的 Shop 提交。它负责 Shop 包内的集成组件：

- `FoundationShopUserAdapter`：将 `UserIdentity` 映射为 `ShopUser`
- `ShopAuthDemoServerMain`：在同一个 Router 上组合用户和买家 Shop Handler
- `ShopAuthDemoClientMain`：打开 `LoginFrame`，登录成功后安装 Shop UI
- 可重复生成的本地数据库初始化器
- 适用于 Windows 的服务端和客户端启动说明

这些组件用于验证联合运行环境，并保持用户模块与 Shop 模块各自的开发边界。

## 4. 会话与权限契约

Demo 中的每个 Shop 请求都携带 `ClientConnection` 保存的不透明令牌。适配器通过当前鉴权契约取得 `UserIdentity`，只向 Shop 投影用户编号、角色类型和有效状态。

- 缺少或已经过期的会话映射为 `AUTH_SESSION_EXPIRED`。
- 首次改密受限会话映射为 `AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED`。
- 有效学生和教师具备买家能力。
- 会话令牌只保存在客户端内存中，不进入界面状态、数据库业务表或日志。

账户创建、凭据校验、密码修改、角色变更、会话签发和登出由用户模块提供。

## 5. Socket 命令

买家 Demo 注册以下命令：

- `SHOP_HOME`
- `SHOP_SEARCH_PRODUCTS`
- `SHOP_GET_PRODUCT`
- `SHOP_GET_SHOP`
- `SHOP_GET_SHOP_PRODUCTS`
- `SHOP_GET_CART`
- `SHOP_CART_ADD`
- `SHOP_CART_UPDATE`
- `SHOP_CART_REMOVE`
- `SHOP_CHECKOUT`
- `SHOP_SIMULATE_PAYMENT`

每个 Handler 校验请求体类型，在需要时解析已登录用户，只调用一个 Shop Service 操作，并把预期领域异常映射为 `ShopErrorCode` 中的稳定错误码。写命令沿用协议中的 `requestId` 作为幂等键。

## 6. 客户端行为

`ShopClientService` 为每个已注册命令提供一个类型安全的异步方法，并把失败的 `ResponseBody` 转换为携带稳定错误码的 Shop 客户端异常。它使用现有连接超时设置，由 `ClientConnection` 自动附加会话令牌。

所有 UI 完成回调都切回 Swing EDT。操作进行期间禁用发起操作的控件，并抑制重复提交；页面在出现更新的请求或已经释放后忽略过时结果。

- 库存不足时保留购物车并显示 `SHOP_INSUFFICIENT_STOCK`。
- 价格变化时展示最新价格，要求用户明确确认后再次结算。
- 会话过期时返回 Demo 组合层提供的登录入口。
- 模拟支付失败后，聚合支付单保持待支付状态并允许在预留期内重试。
- 重复收到支付成功响应时刷新同一个终态，库存和销量仍只发生一次变化。

导航使用基于 ID 的路由和共享 `PageNavigator`。首页和搜索路由保存当前查询条件，返回页面时恢复筛选与排序状态。

## 7. 本地 Demo 数据库

数据库初始化器依据公共、用户和 Shop Schema，以及角色权限种子数据，生成 `vcampus-shop-auth-demo.accdb`。随后写入：

- 一个有效买家账户：`DEMO_BUYER` / `DemoPassword7`
- 两个有效店铺及各自店主
- 包含启用和停用 SKU 的商品
- 足够完成普通购买的库存，以及用于验证库存错误的低库存 SKU

密码哈希调用用户模块现有密码组件。Shop 数据由 Shop Demo 初始化器使用固定标识写入，保证自动断言和人工查看数据库时结果可重复。再次初始化时只替换专用 Demo 数据库文件。

## 8. 业务日志与隐私

Shop 使用 SLF4J 和现有 `vcampus.business` Logger。当前 Logback 配置将业务事件写入 `logs/business.log`；服务启动和无法恢复的运行错误写入 `logs/server.log`。

Handler 完成事件包含：

- 命令名称和 `requestId`
- 成功完成身份验证后的内部 `userId`
- 稳定结果码
- 执行耗时毫秒数

改变状态的业务事件还包含必要的业务标识：

- 结算：订单组编号、商品项数、子订单数和金额
- 支付：支付单编号、模拟渠道、金额和结果

日志内容排除凭据、完整会话令牌、卡号或支付账号、验证码、序列化请求体和完整 DTO。`tblPaymentAttempt` 是支付尝试的持久业务记录，文本日志用于运行排错。

## 9. 验证

### 9.1 Handler 测试

验证准确的命令注册、请求体类型、会话投影、稳定错误映射，以及每个请求只调用一次业务服务。

### 9.2 客户端测试

验证命令名称与 DTO、异步结果映射、超时传递和稳定失败码保留。

### 9.3 UI 与导航测试

验证路由历史、查询恢复、EDT 安全更新、忙碌控件、过时响应保护、错误后的购物车保留、价格变化确认和支付重试。

### 9.4 日志与隐私测试

使用测试 Appender 捕获 `vcampus.business` Logger。验证成功和失败事件字段，并用易识别的测试凭据和令牌扫描输出，证明敏感值没有进入日志。

### 9.5 Socket 端到端测试

使用临时 Access 数据库和随机本地端口启动组合服务端。通过 `ClientConnection` 执行登录、商品查询、加购、结算和模拟支付成功，最终验证库存、预留、购物车、订单、支付、支付尝试、销量和业务日志。

### 9.6 人工验收

提供两条 PowerShell 命令，分别启动 Demo 服务端和客户端。人工运行使用文档中的买家账号，并生成可以检查的 `.accdb` 文件、`logs/server.log` 和 `logs/business.log`。

## 10. 交付顺序

1. 在 `feat/shop-only` 实现并评审 Shop Handler 和业务日志边界。
2. 实现并评审 `ShopClientService` 及其测试。
3. 实现买家路由和 Swing 购物主链路。
4. 在 Shop 功能分支完成模块测试。
5. 在独立 worktree 中建立 `demo/shop-auth` 并加入登录组合组件。
6. 运行 Socket 自动验收和双终端人工 Demo。
7. 在用户模块契约进入共享基线前，保留集成分支作为演示环境。
