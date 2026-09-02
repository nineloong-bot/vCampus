# Task 7 Report: 我的页面与已支付订单出口

## 结果

- 新增 `MyShopPanel(UserView, ShopClientPort, ShopUiKit, Runnable)`，只读展示 `userId`、`loginId`、`role`、`accountStatus`。
- 已支付订单保持 `PaidOrderHistory.orders()` 的服务端顺序；每单使用稳定命名卡片，初始折叠，展开后展示商品与商品 ID、SKU 与 SKU ID、数量、单价、行金额、总额、`paidAt`、状态。
- `UserView` 从登录结果经 `ShopAuthDemoClientMain`、`ShopUiInstaller`、`ShopPageCoordinator` 传入固定 `shop.my` 页面。
- `ShopRoute.My` 进入时加载已支付订单；工具栏 My 的普通历史导航、支付结果页两个 reset 出口及返回安全性由组合测试覆盖。
- My 页面使用 `ShopUiKit.stateView` 表达 INITIAL、LOADING、NORMAL、EMPTY、ERROR、DISCONNECTED；会话过期调用共享 `sessionExpired`。

## RED

1. 新页面与组合入口红灯：
   - 命令：`mvn -pl vcampus-client -am '-Dtest=ShopUiTest,ShopAuthDemoClientMainTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
   - 结果：失败；编译器明确报告缺少 `MyShopPanel` 与 `installAuthenticatedShop(...)`。
2. 完整商品/SKU 标识红灯：
   - 命令：`mvn -pl vcampus-client -am '-Dtest=ShopUiTest#myPageShowsReadOnlyIdentityAndExpandsPaidOrdersInServerOrder' '-Dsurefire.failIfNoSpecifiedTests=false' test`
   - 结果：1 个测试失败；订单项文本缺少 `productId` 与 `skuId`。

## GREEN

- 聚焦 UI/组合：39 个测试通过，0 failures/errors/skips。
- 必跑 Shop 三组：
  - 命令：`mvn -pl vcampus-client -am '-Dtest=ShopUiTest,ShopAuthDemoClientMainTest,PurchasePanelsTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - 结果：62 个测试通过，0 failures/errors/skips。
- 完整回归：
  - 命令：`mvn -pl vcampus-client -am test`
  - 结果：common 5、server 160、client 114，共 279 个测试通过，0 failures/errors/skips；reactor `BUILD SUCCESS`。

## UI、异步与历史证据

- UI：稳定组件名覆盖用户字段、订单容器、订单卡、折叠按钮、详情容器、订单项、总额、支付时间、状态；测试确认两单顺序与默认折叠/点击展开。
- 异步：测试先完成较新的请求、后完成旧请求，最终仅保留新结果；dispose 后异常完成不新增状态视图、不触发会话回调；在线会话过期进入 DISCONNECTED 且只触发一次共享回调。
- 组合生命周期：固定页面注册数仍为 8；My 成为第 7 个异步页面，统一接收会话回调并由 coordinator dispose。
- 历史：工具栏 My 从来源路由普通打开并可返回；成功支付的“继续购物”“查看已支付订单”分别 reset 到 Home/My，历史清空，返回不会进入结算或支付结果。

## 自查

- 对照任务简报逐项检查页面字段、订单字段、服务端顺序、折叠行为、稳定命名、状态视图、LatestRequest、会话过期、UserView 组合传递、路由历史与支付出口。
- 审计全部 `ShopUiInstaller.install` 与 `ShopPageCoordinator` 构造调用；调用方均显式提供登录 `UserView`。
- `git diff --check` 无空白错误；User 模块实现未修改；`logs/` 保持未跟踪并不纳入提交。

## 疑虑

- 无已知功能性疑虑。
- Maven 仍输出 Mockito 动态 agent 的未来兼容性警告，以及工作区 LF/CRLF 转换提示；本次测试结果未受影响。

## Fix Round 1：已支付订单滚动布局

### 修复

- `my.orders` 改为按 preferred height 纵向排列的 `BoxLayout.Y_AXIS` 内容容器，并置于稳定命名 `my.orders.scroll` 的仅垂直 `JScrollPane` 中。
- 展开或折叠订单时失效并重算订单容器布局，随后 revalidate/repaint，使 viewport 与滚动条采用最新 preferred height。
- 现有订单、详情、状态视图组件名与异步状态流保持稳定。

### 测试文件

- `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`
- 新增 `myPageKeepsEveryOrderAndExpandedLastItemReachableInASmallViewport`：使用 360×180 小视口、6 笔订单和末单 8 行明细，验证列表实际高度不小于 preferred height、滚动范围大于 viewport、展开后范围增长、滚到底可覆盖最后订单与最后明细行。

### RED / GREEN

- RED：`mvn -pl vcampus-client -am '-Dtest=ShopUiTest#myPageKeepsEveryOrderAndExpandedLastItemReachableInASmallViewport' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - 结果：1 个测试失败；现有列表实际高度 122，小于 preferred height 270。
- 聚焦 GREEN：同一命令，1/1 通过，0 failures/errors/skips。
- Shop UI：`mvn -pl vcampus-client -am '-Dtest=ShopUiTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - 结果：30/30 通过，0 failures/errors/skips。
- 完整回归：`mvn -pl vcampus-client -am test`
  - 结果：common 5、server 160、client 115，共 280 个测试通过，0 failures/errors/skips；reactor `BUILD SUCCESS`。

### 自查

- 小视口下折叠态列表保留完整 preferred height，垂直滚动条可到达底部。
- 展开末单多行明细后滚动 maximum 增长，viewport 底边覆盖末单和最后明细行。
- `git diff --check` 无空白错误；`logs/` 保持未跟踪。
