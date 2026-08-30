# vCampus Shop Experience and Paid Orders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不修改共享框架的前提下交付单一商城入口、状态化 Shop 导航、统一搜索与首页、100 件 Demo 商品，以及当前买家的已支付订单页面。

**Architecture:** `ShopUiInstaller` 将 Shop 自有根容器嵌入共享 `page.shop` 占位页，并复用 `navigation.shop`；根容器内部使用固定卡片、公共工具栏和 `ShopNavigator`。服务端扩展 Access 查询和买家订单只读服务，客户端通过现有 Socket 抽象异步调用。Demo 初始化器生成确定性目录和订单夹具。

**Tech Stack:** Java 21、Maven、Swing、Java Object Socket、UCanAccess/Access、JUnit 5、AssertJ、Mockito、PowerShell/Pester。

**Spec:** `docs/superpowers/specs/2026-08-30-vcampus-shop-experience-and-orders-design.md`

## Global Constraints

- 只修改 `common/shop`、`server/shop`、`client/shop`、Shop 数据库、Demo、测试和说明文档。
- 不修改 Foundation、User、Socket、Router、事务框架和公共网络接口实现。
- 每个任务先写失败测试，再做最小实现，再运行目标回归测试，最后才本地提交。
- 本计划只允许本地提交；不得 push、merge、rebase、删除、回滚或清理文件。
- 所有 `git add` 使用显式路径；不得加入或删除未跟踪的 `logs/`。

---

### Task 1: 接管现有“校园商城”入口

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopUiInstaller.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopModulePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`

**Interface:** `ShopUiInstaller` 查找唯一的 `navigation.shop` 与 `page.shop`；`ShopModulePanel` 提供 Shop 内部 `register/show`；`InstalledCoordinator.enter()` 首次打开默认首页，之后恢复当前卡片。

- [ ] **Step 1: 写失败测试**：断言安装前后侧栏按钮数不变、只存在一个 `navigation.shop`、不创建 `shop.navigation`；点击原按钮后 `enter()` 一次；第二次进入不会把首页重复压栈；找不到或找到多个稳定组件名时快速失败。
- [ ] **Step 2: 运行红灯**：`mvn -pl vcampus-client -am -Dtest=ShopUiTest test`；预期现实现因新增按钮和共享页面重复注册语义而失败。
- [ ] **Step 3: 实现 Shop 根卡片**：`ShopModulePanel` 使用内部 `CardLayout` 和唯一 ID 集合实现 `ShopPageCoordinator.CardNavigator`；协调器注册 `shop.home/search/product/storefront/cart/checkout/payment-result` 到该根容器，不再调用共享 `PageNavigator.register`。
- [ ] **Step 4: 实现稳定组件查找和接管**：递归按 `Component.getName()` 查找 `navigation.shop` 与 `page.shop`；将根容器加入占位页 `BorderLayout.CENTER`；给原 `AbstractButton` 添加进入监听器；窗口关闭仍只调用一次 `dispose()`。
- [ ] **Step 5: 运行绿灯和客户端回归**：`mvn -pl vcampus-client -am -Dtest=ShopUiTest,ShopAuthDemoClientMainTest test`。
- [ ] **Step 6: 本地提交**：显式暂存上述四个路径，提交 `feat(shop): reuse shared campus shop entry`。

### Task 2: 公共工具栏、历史与精确视图恢复

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/HomeViewState.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/SearchViewState.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/StorefrontViewState.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopRoute.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigator.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopToolbar.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/CartCountModel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopModulePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ShopHomePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductSearchPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/BuyerShopPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductDetailPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CartPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CheckoutPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/PaymentResultPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`

**Interfaces:** `ShopRouteHost.capture(route)` 返回当前含滚动状态的路由；`ShopNavigator` 增加 `canGoBack()`、状态监听、`replaceCurrent(route)`、`reset(route)`、`renderCurrent()`；状态记录分别持有原查询、滚动值和搜索筛选展开状态；本任务同时加入无负载的 `ShopRoute.My`，供工具栏和支付安全出口导航，页面内容在 Task 7 接通。

- [ ] **Step 1: 写导航红灯测试**：首页无历史禁用返回；`Home(page=2,scroll=360) -> Product -> back` 精确恢复；搜索恢复关键词/分类/价格/排序/页码/展开状态/滚动；店铺恢复查询与滚动；购物车和“我的”返回来源页；历史仍保持 20 条上限。
- [ ] **Step 2: 写工具栏红灯测试**：所有路由显示标题、我的和返回；搜索路由隐藏购物车，其余显示；购物车文案是所有行数量之和；加购、修改、删除和成功支付会更新数量。
- [ ] **Step 3: 运行红灯**：`mvn -pl vcampus-client -am -Dtest=ShopUiTest test`。
- [ ] **Step 4: 实现状态捕获/恢复**：三个列表面板以 `JScrollPane` 承载结果；离开时生成不可变状态，重新加载后在 EDT 用 `verticalScrollBar.setValue(savedY)`；`ShopNavigator.open()` 先调用 host 捕获当前路由再压栈。
- [ ] **Step 5: 实现公共工具栏和数量模型**：`ShopModulePanel` 北部仅放一个 `ShopToolbar`；工具栏观察导航和 `CartCountModel`；页面只报告最新 `CartView.totalQuantity()`，不直接修改标签。
- [ ] **Step 6: 实现支付安全出口**：结算成功以 `replaceCurrent(PaymentResult)` 替换结算；结果页提供“继续购物”=`reset(defaultHome)`、“查看已支付订单”=`reset(My)`；Task 7 接通真实页面前由协调器的固定 My 占位卡承接该路由，返回不再进入已完成结算。
- [ ] **Step 7: 运行绿灯和完整客户端 Shop 测试**：`mvn -pl vcampus-client -am -Dtest=ShopUiTest test`，再执行 `mvn -pl vcampus-client -am test`。
- [ ] **Step 8: 本地提交**：显式暂存本任务文件，提交 `feat(shop): add stateful shop toolbar navigation`。

### Task 3: 单输入框统一搜索与仓储匹配

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/AccessShopRepository.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductSearchPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ShopHomePanel.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/repository/AccessShopRepositoryTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ShopServiceTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`

**Query rule:** 在现有可售 SKU 聚合之外增加：

```sql
AND (p.productName LIKE ? OR s.shopName LIKE ? OR p.category LIKE ?
 OR p.description LIKE ? OR EXISTS (
   SELECT 1 FROM tblProductSku matched
   WHERE matched.productId = p.productId AND matched.skuName LIKE ?))
```

- [ ] **Step 1: 写仓储红灯测试**：分别用商品名、店铺名、四大分类、描述词、SKU 名命中；同一商品两个 SKU 同时命中时 `totalItems == 1` 且只返回一次；价格、分类、排序与分页仍组合生效。
- [ ] **Step 2: 写 UI 红灯测试**：初始仅有 `keyword` 与 `search`；首次提交完成后出现 `search.filters.toggle`；展开后只出现四分类下拉、最低/最高价和排序；返回恢复值和展开状态。
- [ ] **Step 3: 运行红灯**：`mvn -pl vcampus-server -am -Dtest=AccessShopRepositoryTest,ShopServiceTest test` 和 `mvn -pl vcampus-client -am -Dtest=ShopUiTest test`。
- [ ] **Step 4: 实现参数绑定**：对同一规范化 `%keyword%` 绑定五次，保留外层 `GROUP BY`/`MIN(k.unitPrice)`；不要把匹配 SKU 直接连接进价格聚合。
- [ ] **Step 5: 实现单输入框 UI**：首页内联关键词框直接打开 `Search(SearchViewState)`；搜索页把筛选控件移入默认隐藏容器，分类使用固定四项和“全部”；每次提交构造新的路由状态并通过导航打开/替换以保存历史。
- [ ] **Step 6: 运行绿灯与回归**：重复 Step 3 命令，并执行 `mvn -pl vcampus-server -am -Dtest=ShopServiceTest test`。
- [ ] **Step 7: 本地提交**：提交 `feat(shop): unify catalog keyword search`。

### Task 4: 首页四分类与“猜你喜欢”

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ShopService.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ShopHomePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductCardsPanel.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ShopServiceTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`

- [ ] **Step 1: 写红灯测试**：即使传入其他排序，首页仓储调用仍为 `SALES_DESC`；页面组件顺序为搜索、`home.categories`、`home.recommendations`；四个分类按钮精确打开对应分类搜索；卡片可读取商品、店铺、分类、起售价、销量。
- [ ] **Step 2: 运行红灯**：分别运行 `ShopServiceTest` 与 `ShopUiTest`。
- [ ] **Step 3: 实现**：`getHomeProducts` 固定 `SALES_DESC`；首页创建四个语义按钮和“猜你喜欢”标题；`ProductCardsPanel` 给五类展示字段稳定组件名，保持整卡可打开详情。
- [ ] **Step 4: 运行绿灯与客户端/服务端目标回归**：`mvn -pl vcampus-server -am -Dtest=ShopServiceTest test`；`mvn -pl vcampus-client -am -Dtest=ShopUiTest test`。
- [ ] **Step 5: 本地提交**：提交 `feat(shop): add categories and recommendations home`。

### Task 5: 四店 100 件确定性 Demo 商品

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopDemoCatalog.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabase.java`
- Modify: `vcampus-database/demo/SHOP_AUTH_DEMO.md`
- Modify: `vcampus-database/demo/SHOP_AUTH_DEMO_TEAM_TESTING.md`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabaseTest.java`

**Catalog construction:** `ShopDemoCatalog` 暴露不可变 `List<ProductSeed>`；文具使用 10 个固定品名，图书使用 30 个固定书名，生活用品使用 11 个固定品类（纸巾、洗衣液、洗发水、沐浴露、牙膏、毛巾、水杯、雨伞、收纳盒、垃圾袋、清洁剂）与 5 个固定规格词组合，药品使用 5 个固定品名。ID 为 `demo-{category}-{001..}`，销量、价格和库存由索引确定但落库为固定值；每第 5 件商品增加第二 SKU。

- [ ] **Step 1: 写数据库红灯测试**：断言商品总数 100；按店铺/分类计数精确为 10/30/55/5；商品名、商品 ID 和 SKU ID 无重复；每件至少一项可售 SKU；销量不全相同；首页前两页全局销量非升序；SKU 关键词命中商品且不重复。
- [ ] **Step 2: 运行红灯**：`mvn -pl vcampus-server -am -Dtest=ShopAuthDemoDatabaseTest test`。
- [ ] **Step 3: 实现目录与四店账号**：新增生活超市和药店店主用户；写入四个店铺及正确分类；批量遍历不可变清单写商品与 SKU；描述包含店铺、分类、用途和规格搜索词。
- [ ] **Step 4: 更新人工测试文档**：记录 100 件分配、可用搜索词、分类/分页/销量排序检查点；保留 `DEMO_BUYER` / `DemoPassword7`。
- [ ] **Step 5: 运行绿灯与 Demo 数据回归**：`mvn -pl vcampus-server -am -Dtest=ShopAuthDemoDatabaseTest,ShopDemoTest test`。
- [ ] **Step 6: 本地提交**：显式暂存目录、初始化器、测试和两份文档，提交 `feat(shop-demo): seed one hundred catalog products`。

### Task 6: 当前买家的已支付订单查询链路

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/PaidOrderItemView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/PaidOrderView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/PaidOrderHistory.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/ShopRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/AccessShopRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/BuyerOrderService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/BuyerShopHandlers.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoRuntime.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientPort.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientService.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/shop/PaidOrderHistoryTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/BuyerOrderServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler/BuyerShopHandlersTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/service/ShopClientServiceTest.java`

**DTOs:** `PaidOrderItemView(productId, productName, skuId, skuName, quantity, unitPrice, lineAmount)`；`PaidOrderView(orderId, orderNumber, shopId, shopName, totalAmount, paidAt, OrderStatus.PAID, items)`；`PaidOrderHistory(List<PaidOrderView>)` 在紧凑构造器中 `List.copyOf`。

- [ ] **Step 1: 写 Common 红灯测试**：验证 DTO Java 序列化往返、空值约束、金额/数量约束和列表防御性复制。
- [ ] **Step 2: 写 Server 红灯测试**：两个买家各有已支付订单，另有待支付订单；服务仅返回传入 buyerId 的 `PAID`，按 `paidAt DESC, orderId`，并含完整明细。
- [ ] **Step 3: 写协议红灯测试**：`SHOP_GET_PAID_ORDERS` 只接收 `EmptyRequest`；Handler 使用会话解析得到的 `actor.userId()`，不接受客户端 buyerId；客户端发送正确命令并保留稳定错误码。
- [ ] **Step 4: 运行红灯**：依次运行 `PaidOrderHistoryTest`、`BuyerOrderServiceTest,BuyerShopHandlersTest`、`ShopClientServiceTest`。
- [ ] **Step 5: 实现仓储查询**：主查询连接 `tblOrder`、`tblOrderGroup`、`tblShop`，条件为 `g.buyerUserId = ? AND g.groupStatus = 'PAID' AND o.orderStatus = 'PAID' AND o.paidAt IS NOT NULL`；逐订单按 `orderId` 读取 `tblOrderItem`，最终排序 `o.paidAt DESC, o.orderId`。
- [ ] **Step 6: 实现服务、Handler 与客户端**：`BuyerOrderService.getPaidOrders(String buyerUserId)` 在只读事务中组装 DTO；为 `BuyerShopHandlers` 注入该服务并注册命令；`ShopClientPort/Service.getPaidOrders()` 返回 `CompletableFuture<PaidOrderHistory>`。
- [ ] **Step 7: 运行绿灯和三模块回归**：重复 Step 4，再运行 `mvn -pl vcampus-common clean verify`、`mvn -pl vcampus-server -am test`、`mvn -pl vcampus-client -am test`。
- [ ] **Step 8: 本地提交**：提交 `feat(shop): query buyer paid order history`。

### Task 7: “我的”页面与支付后订单出口

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/MyShopPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopUiInstaller.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopToolbar.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/PaymentResultPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMain.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMainTest.java`

- [ ] **Step 1: 写 UI 红灯测试**：安装器必须接收登录 `UserView`；点击“我的”打开 `ShopRoute.My`；页面只读显示 userId/loginId/role/status；订单默认折叠、点击可展开明细；顺序保持服务端 paidAt 倒序；返回恢复来源路由。
- [ ] **Step 2: 写支付出口红灯测试**：成功结果页文案是“继续购物”“查看已支付订单”；前者重置首页，后者重置“我的”；两条路径均无法返回结算/支付结果。
- [ ] **Step 3: 运行红灯**：`mvn -pl vcampus-client -am -Dtest=ShopUiTest,ShopAuthDemoClientMainTest test`。
- [ ] **Step 4: 实现页面**：`MyShopPanel(UserView, ShopClientPort, ShopUiKit, Runnable)` 使用 `LatestRequest`；用户信息为 `JLabel`；每个订单使用带稳定名称的折叠面板；失败和会话过期沿用现有状态视图。
- [ ] **Step 5: 接通组合层**：`showMain` 将 `result.user()` 传给 `ShopUiInstaller.install`；协调器固定注册 `shop.my`；工具栏 My 按钮执行普通 `open(new ShopRoute.My())`。
- [ ] **Step 6: 运行绿灯与客户端回归**：重复 Step 3，并执行 `mvn -pl vcampus-client -am test`。
- [ ] **Step 7: 本地提交**：提交 `feat(shop): add buyer profile and paid orders page`。

### Task 8: Demo 已支付订单、端到端与最终验证

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabase.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabaseTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthEndToEndTest.java`
- Modify: `vcampus-database/demo/SHOP_AUTH_DEMO.md`
- Modify: `vcampus-database/demo/SHOP_AUTH_DEMO_TEAM_TESTING.md`

- [ ] **Step 1: 写 Demo 红灯测试**：初始化器写入 `demo-buyer` 的两笔 PAID（不同 paidAt）、另一买家一笔 PAID、当前买家一笔 PENDING；Socket 登录后仅返回当前买家两笔 PAID 且明细正确。
- [ ] **Step 2: 运行红灯**：`mvn -pl vcampus-server -am -Dtest=ShopAuthDemoDatabaseTest test`；`mvn -pl vcampus-client -am -Dtest=ShopAuthEndToEndTest test`。
- [ ] **Step 3: 实现固定订单夹具**：写入订单组、子订单、明细与成功支付记录；金额与对应目录 SKU 一致；所有时间为固定 UTC 值，确保倒序断言稳定。
- [ ] **Step 4: 更新人工验收指南**：覆盖单入口、四分类、统一搜索五种匹配、筛选显隐、分页/滚动返回、购物车返回、我的订单隔离与支付后两个出口。
- [ ] **Step 5: 运行完整自动验证**：
  - `mvn -pl vcampus-common clean verify`
  - `mvn -pl vcampus-server -am test`
  - `mvn -pl vcampus-client -am test`
  - `pwsh -File vcampus-distribution/scripts/tests/start-shop-auth-demo-scripts.tests.ps1`
  - PowerShell AST：对 `start-shop-auth-demo-server.ps1` 与 `start-shop-auth-demo-client.ps1` 调用 `[System.Management.Automation.Language.Parser]::ParseFile(...)`，断言错误集合为空。
  - `git diff --check`
  - `git status --short --branch`，确认只有计划内变更/提交且 `logs/` 仍为未跟踪、未暂存。
- [ ] **Step 6: 本地提交**：提交 `test(shop-demo): cover expanded buyer shop experience`；不 push。

## Plan Review Checklist

- [ ] 每个验收场景至少有一个自动测试或明确人工步骤。
- [ ] 所有新协议数据位于 `common/shop`，所有业务实现位于 Shop 范围。
- [ ] 没有修改共享外壳、用户、Socket、Router、事务或公共网络接口。
- [ ] 单入口通过稳定组件名接管，不依赖组件索引，不重复注册共享 page ID。
- [ ] SKU 关键词使用 `EXISTS`，不会造成商品重复或污染最低价聚合。
- [ ] 买家编号只来自认证会话，订单查询不接受客户端指定用户。
- [ ] Demo 商品总数与四店分配有数据库级断言。
- [ ] 所有提交显式排除 `logs/`，且没有远端写操作。
