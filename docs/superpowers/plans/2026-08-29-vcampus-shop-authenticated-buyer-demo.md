# vCampus Shop Authenticated Buyer Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付真实登录、真实 Socket、本地 Access 数据库和 Swing 买家界面组成的 Shop 购物闭环。

**Architecture:** `feat/shop-only` 先实现 Shop 日志、Buyer Handler、异步客户端和买家页面，并通过 Shop 内部端口保持独立编译。随后在独立 `demo/shop-auth` worktree 中组合用户模块会话、Shop 提交和本地演示数据，完成真实登录到模拟支付的端到端验证。

**Tech Stack:** Java 21、Maven、Swing、Java Object Socket、UCanAccess/Access、SLF4J 2.0.17、Logback 1.5.18、JUnit 5、AssertJ、Mockito。

**Spec:** `docs/superpowers/specs/2026-08-29-vcampus-shop-authenticated-buyer-demo-design.md`

## Global Constraints

- 正式功能提交只修改 `common/shop`、`server/shop`、`client/shop`、Shop 测试和 Shop 文档。
- Foundation、Router、Socket、事务、异步客户端、`MainFrame` 和用户模块只通过现有公开接口使用。
- `feat/shop-only` 与 `demo/shop-auth` 始终使用独立 worktree；集成提交留在 Demo 分支。
- 所有网络调用返回 `CompletableFuture`，Swing EDT 不执行阻塞 I/O。
- 金额只使用 `BigDecimal`；写命令使用协议 `requestId` 作为幂等键。
- 日志使用 `vcampus.business`，并排除凭据、完整会话令牌、支付账号、卡号、验证码、请求体和完整 DTO。
- 首批页面为商城首页、搜索、商品详情、店铺主页、购物车、结算、模拟收银台和支付结果。
- Shop 页面遵循 `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md`，只通过注入的 `ShopUiKit` 表达语义样式；业务页面禁止声明私有颜色、字体和边框。公共 UI 未进入当前基线时，`DefaultShopUiKit` 只使用标准 Swing 控件和 `UIManager` 默认值。
- Task 6 订单履约、店主页面和管理员页面在本计划验收后继续开发。

---

### Task 1: Shop 鉴权错误边界与业务日志

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/port/ShopAccessException.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/logging/ShopBusinessLogger.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/logging/ShopBusinessLoggerTest.java`

**Interfaces:**
- Consumes: `org.slf4j.LoggerFactory.getLogger("vcampus.business")`、`Message`、`ResponseBody`、`CheckoutResult`、`PaymentView`。
- Produces: `ShopAccessException(String code)`、`commandCompleted(...)`、`checkoutSucceeded(...)`、`paymentCompleted(...)`，供 Task 2 和集成适配器使用。

- [ ] **Step 1: 写日志隐私失败测试**

```java
@Test
void writesStructuredBusinessFieldsAndOmitsTokenAndCredentials() {
    Logger logger = (Logger) LoggerFactory.getLogger("vcampus.business");
    ListAppender<ILoggingEvent> events = new ListAppender<>();
    events.start();
    logger.addAppender(events);
    try {
        Message request = new Message("request-1", MessageType.REQUEST,
                "SHOP_CHECKOUT", "token-never-log", EmptyRequest.INSTANCE, 1L);
        ShopBusinessLogger shopLog = new ShopBusinessLogger();
        shopLog.commandCompleted(request, "buyer-1", "SUCCESS", 17L);
        String output = events.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertThat(output).contains("command=SHOP_CHECKOUT", "requestId=request-1",
                "userId=buyer-1", "code=SUCCESS", "durationMs=17");
        assertThat(output).doesNotContain("token-never-log", "DemoPassword7");
    } finally {
        logger.detachAppender(events);
    }
}
```

- [ ] **Step 2: 运行测试并确认缺少日志类**

Run: `mvn -pl vcampus-server -am -Dtest=ShopBusinessLoggerTest test`

Expected: FAIL，编译器报告 `ShopBusinessLogger` 不存在。

- [ ] **Step 3: 实现稳定鉴权错误和业务日志**

```java
public final class ShopAccessException extends RuntimeException {
    private final String code;

    public ShopAccessException(String code) {
        super(Objects.requireNonNull(code, "code"));
        this.code = code;
    }

    public String code() { return code; }
}
```

```java
public final class ShopBusinessLogger {
    private static final org.slf4j.Logger LOG =
            LoggerFactory.getLogger("vcampus.business");

    public void commandCompleted(Message request, String userId,
            String code, long durationMs) {
        LOG.info("module=SHOP command={} requestId={} userId={} code={} durationMs={}",
                request.command(), request.requestId(), safe(userId), code, durationMs);
    }

    public void checkoutSucceeded(Message request, String userId,
            CheckoutCommand command, CheckoutResult result) {
        LOG.info("module=SHOP event=CHECKOUT requestId={} userId={} orderGroupId={} "
                        + "itemCount={} orderCount={} amount={}",
                request.requestId(), safe(userId), result.orderGroupId(),
                command.items().size(),
                result.orders().size(), result.totalAmount());
    }

    public void paymentCompleted(Message request, String userId, PaymentView result) {
        LOG.info("module=SHOP event=PAYMENT requestId={} userId={} paymentId={} "
                        + "channel={} amount={} result={}",
                request.requestId(), safe(userId), result.paymentId(),
                result.successfulChannel(), result.amount(), result.status());
    }

    private static String safe(String value) { return value == null ? "anonymous" : value; }
}
```

- [ ] **Step 4: 运行日志测试和隐私扫描**

Run: `mvn -pl vcampus-server -am -Dtest=ShopBusinessLoggerTest test`

Expected: PASS，捕获的日志包含结构化字段且不包含测试令牌或密码。

- [ ] **Step 5: 提交日志边界**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/shop/port/ShopAccessException.java vcampus-server/src/main/java/edu/seu/vcampus/server/shop/logging/ShopBusinessLogger.java vcampus-server/src/test/java/edu/seu/vcampus/server/shop/logging/ShopBusinessLoggerTest.java
git commit -m "feat(shop): add business logging boundary"
```

### Task 2: 买家 Socket Handler 与请求幂等

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/BuyerShopHandlers.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler/BuyerShopHandlersTest.java`

**Interfaces:**
- Consumes: `MessageRouter.register`、`RequestDeduplicator.executeOnce`、`ShopUserPort.requireUser`、Task 1 日志，以及现有 `ShopService`、`CartService`、`CheckoutService`、`SimulatedPaymentService`。
- Produces: 构造器 `BuyerShopHandlers(MessageRouter, ShopUserPort, RequestDeduplicator, ShopService, CartService, CheckoutService, SimulatedPaymentService, ShopBusinessLogger)`，注册设计文档中的 11 个命令。

- [ ] **Step 1: 写命令注册、错误映射和幂等失败测试**

```java
@Test
void registersBuyerSurfaceAndReplaysIdenticalCartWrite() {
    new BuyerShopHandlers(router, users, deduplicator, shop, cart,
            checkout, payment, businessLog);
    Message add = request("request-add", "SHOP_CART_ADD",
            new AddCartItemCommand("sku-1", 2));
    when(users.requireUser("buyer-token"))
            .thenReturn(new ShopUser("buyer-1", ShopUserKind.STUDENT, true));
    when(deduplicator.executeOnce(eq(add), eq("buyer-1"), eq("connection-1"), any()))
            .thenAnswer(invocation -> invocation.<Supplier<ResponseBody<CartView>>>getArgument(3).get());
    when(cart.addToCart("buyer-token", (AddCartItemCommand) add.body()))
            .thenReturn(cartView());

    assertThat(router.route(add, context()).code()).isEqualTo("SUCCESS");
    verify(cart).addToCart("buyer-token", new AddCartItemCommand("sku-1", 2));
    verify(deduplicator).executeOnce(eq(add), eq("buyer-1"),
            eq("connection-1"), any());
}

@Test
void mapsShopAndAuthenticationFailuresToStableCodes() {
    new BuyerShopHandlers(router, users, deduplicator, shop, cart,
            checkout, payment, businessLog);
    when(users.requireUser("expired"))
            .thenThrow(new ShopAccessException("AUTH_SESSION_EXPIRED"));
    Message expired = new Message("request-expired", MessageType.REQUEST,
            "SHOP_GET_CART", "expired", EmptyRequest.INSTANCE, 1L);
    assertThat(router.route(expired, context()).code())
            .isEqualTo("AUTH_SESSION_EXPIRED");
}

private static Message request(String requestId, String command, Serializable body) {
    return new Message(requestId, MessageType.REQUEST, command,
            "buyer-token", body, 1L);
}

private static ClientContext context() {
    return new ClientContext("connection-1", "127.0.0.1");
}

private static CartView cartView() {
    return new CartView("cart-1", List.of(), BigDecimal.ZERO);
}
```

- [ ] **Step 2: 运行 Handler 测试并确认失败**

Run: `mvn -pl vcampus-server -am -Dtest=BuyerShopHandlersTest test`

Expected: FAIL，编译器报告 `BuyerShopHandlers` 不存在。

- [ ] **Step 3: 实现统一 Handler 包装器和 11 个命令**

```java
router.register("SHOP_HOME", read(HomeProductQuery.class,
        (token, body) -> shop.getHomeProducts(body)));
router.register("SHOP_SEARCH_PRODUCTS", read(ProductSearchQuery.class,
        (token, body) -> shop.searchProducts(body)));
router.register("SHOP_GET_PRODUCT", read(String.class,
        (token, body) -> shop.getProduct(body)));
router.register("SHOP_GET_SHOP", read(String.class,
        (token, body) -> shop.getShop(body)));
router.register("SHOP_GET_SHOP_PRODUCTS", read(ShopProductQuery.class,
        (token, body) -> shop.getShopProducts(body)));
router.register("SHOP_GET_CART", read(EmptyRequest.class,
        (token, body) -> cart.getCart(token)));
router.register("SHOP_CART_ADD", write(AddCartItemCommand.class,
        (token, body) -> cart.addToCart(token, body)));
router.register("SHOP_CART_UPDATE", write(UpdateCartItemCommand.class,
        (token, body) -> cart.updateCartItem(token, body)));
router.register("SHOP_CART_REMOVE", write(String.class,
        (token, body) -> cart.removeCartItem(token, body)));
router.register("SHOP_CHECKOUT", write(CheckoutCommand.class,
        (token, body) -> checkout.checkout(token, body)));
router.register("SHOP_SIMULATE_PAYMENT", write(SimulatePaymentCommand.class,
        (token, body) -> payment.simulatePayment(token, body)));
```

`read` 和 `write` 都先调用 `users.requireUser(message.sessionToken())` 获取 `userId`。`write` 再调用：

```java
deduplicator.executeOnce(message, actor.userId(), context.connectionId(),
        () -> execute(message, actor.userId(), operation));
```

`execute` 将 `ShopException` 映射为 `error.code().name()`，将 `ShopAccessException` 映射为 `error.code()`，将参数错误映射为 `COMMON_VALIDATION_FAILED`，其余异常映射为 `COMMON_INTERNAL_ERROR`；返回前调用 `commandCompleted`。结算成功时把原始 `CheckoutCommand` 和 `CheckoutResult` 一起交给 Task 1 的日志方法，支付成功时记录 `PaymentView`。

- [ ] **Step 4: 运行 Handler、重复请求和日志测试**

Run: `mvn -pl vcampus-server -am -Dtest=BuyerShopHandlersTest,ShopBusinessLoggerTest test`

Expected: PASS，11 个命令可路由，同一写请求由 `RequestDeduplicator` 执行一次，错误响应只包含稳定错误码。

- [ ] **Step 5: 提交买家 Handler**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/BuyerShopHandlers.java vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler/BuyerShopHandlersTest.java
git commit -m "feat(shop): add buyer socket handlers"
```

### Task 3: 类型安全的异步 Shop 客户端

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientPort.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientException.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientService.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ShopClientFixtures.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/service/ShopClientServiceTest.java`

**Interfaces:**
- Consumes: `ClientConnection.send(String, Serializable, Duration)` 和现有 Shop DTO。
- Produces: `ShopClientService(ClientConnection, Duration)` 和 UI 可替换的 `ShopClientPort`，其 11 个方法均返回 `CompletableFuture<T>`。

- [ ] **Step 1: 写客户端命令和失败码测试**

```java
@Test
void sendsCartAddAndReturnsTypedCart() {
    CartView expected = cartView();
    when(connection.<CartView>send(eq("SHOP_CART_ADD"),
            eq(new AddCartItemCommand("sku-1", 2)), eq(TIMEOUT)))
            .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(expected)));
    assertThat(service.addToCart(new AddCartItemCommand("sku-1", 2)).join())
            .isEqualTo(expected);
}

@Test
void preservesStableServerCode() {
    when(connection.<CartView>send(eq("SHOP_GET_CART"),
            eq(EmptyRequest.INSTANCE), eq(TIMEOUT)))
            .thenReturn(CompletableFuture.completedFuture(ResponseBody.failure(
                    "AUTH_SESSION_EXPIRED", "请求未能完成", null)));
    assertThatThrownBy(() -> service.getCart().join())
            .hasRootCauseInstanceOf(ShopClientException.class)
            .hasRootCauseMessage("AUTH_SESSION_EXPIRED");
}
```

- [ ] **Step 2: 运行客户端测试并确认失败**

Run: `mvn -pl vcampus-client -am -Dtest=ShopClientServiceTest test`

Expected: FAIL，编译器报告 Shop 客户端类型不存在。

- [ ] **Step 3: 定义端口并实现统一响应转换**

```java
public interface ShopClientPort {
    CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query);
    CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query);
    CompletableFuture<ProductDetail> getProduct(String productId);
    CompletableFuture<ShopDetail> getShop(String shopId);
    CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query);
    CompletableFuture<CartView> getCart();
    CompletableFuture<CartView> addToCart(AddCartItemCommand command);
    CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command);
    CompletableFuture<CartView> removeCartItem(String cartItemId);
    CompletableFuture<CheckoutResult> checkout(CheckoutCommand command);
    CompletableFuture<PaymentView> simulatePayment(SimulatePaymentCommand command);
}
```

```java
private <T extends Serializable> CompletableFuture<T> send(
        String command, Serializable body) {
    return connection.<T>send(command, body, timeout).thenApply(response -> {
        if (!response.success() || response.data() == null) {
            throw new ShopClientException(response.code());
        }
        return response.data();
    });
}
```

`getCart()` 发送 `EmptyRequest.INSTANCE`；其余方法使用 Task 2 的准确命令名和对应 DTO。

测试夹具使用固定、类型准确的 DTO：

```java
public final class ShopClientFixtures {
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    public static ProductSummary productSummary() {
        return new ProductSummary("product-1", "shop-1", "校园文具店",
                "签字笔", "文具", new BigDecimal("3.00"), 4, NOW);
    }

    public static <T extends Serializable> PageResult<T> page(T value) {
        return new PageResult<>(List.of(value), 0, 20, 1);
    }

    public static CartView cartView() {
        CartItemView item = new CartItemView("cart-item-1", "product-1", "签字笔",
                "sku-1", "黑色", "shop-1", "校园文具店",
                new BigDecimal("3.00"), 2, 0);
        return new CartView("cart-1", List.of(item), new BigDecimal("6.00"));
    }

    public static CheckoutResult checkoutResult() {
        OrderSummary order = new OrderSummary("order-1", "group-1", "O0001",
                "shop-1", "校园文具店", new BigDecimal("6.00"),
                OrderStatus.PENDING_PAYMENT, NOW);
        return new CheckoutResult("group-1", "payment-1", "P0001",
                new BigDecimal("6.00"), NOW.plusSeconds(900), List.of(order));
    }

    public static PaymentView pendingPayment() {
        return new PaymentView("payment-1", "group-1", "P0001",
                new BigDecimal("6.00"), PaymentStatus.PENDING, null,
                NOW.plusSeconds(900), null, 0);
    }

    public static PaymentView successfulPayment() {
        return new PaymentView("payment-1", "group-1", "P0001",
                new BigDecimal("6.00"), PaymentStatus.SUCCEEDED,
                PaymentChannel.ALIPAY, NOW.plusSeconds(900), NOW, 1);
    }
}
```

- [ ] **Step 4: 运行客户端测试**

Run: `mvn -pl vcampus-client -am -Dtest=ShopClientServiceTest test`

Expected: PASS，11 个方法均验证命令名、请求体和响应类型。

- [ ] **Step 5: 提交客户端网关**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service vcampus-client/src/test/java/edu/seu/vcampus/client/shop/service vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ShopClientFixtures.java
git commit -m "feat(shop): add asynchronous buyer client"
```

### Task 4: 有界 Shop 路由与固定页面宿主

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopRoute.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigator.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopRouteHost.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigatorTest.java`

**Interfaces:**
- Consumes: `HomeProductQuery`、`ProductSearchQuery`、产品/店铺 ID。
- Produces: `open(ShopRoute)`、`back()`、`current()`、`history()`，以及 `ShopRouteHost.render(ShopRoute)`。

- [ ] **Step 1: 写重复路由、查询恢复和 20 条历史测试**

```java
@Test
void ignoresCurrentRouteAndBoundsRestorableHistory() {
    List<ShopRoute> rendered = new ArrayList<>();
    ShopNavigator navigator = new ShopNavigator(rendered::add);
    ProductSearchQuery pens = new ProductSearchQuery("笔", null, null, null,
            ProductSortMode.SALES_DESC, 0, 20);
    navigator.open(new ShopRoute.Home(new HomeProductQuery(
            null, null, ProductSortMode.SALES_DESC, 0, 20)));
    navigator.open(new ShopRoute.Search(pens));
    navigator.open(new ShopRoute.Product("product-1"));
    navigator.open(new ShopRoute.Product("product-1"));
    IntStream.range(2, 24).forEach(i ->
            navigator.open(new ShopRoute.Product("product-" + i)));
    assertThat(navigator.history()).hasSize(20);
    navigator.back();
    assertThat(navigator.current()).isEqualTo(new ShopRoute.Product("product-22"));
    assertThat(rendered).doesNotHaveDuplicates();
}
```

- [ ] **Step 2: 运行导航测试并确认失败**

Run: `mvn -pl vcampus-client -am -Dtest=ShopNavigatorTest test`

Expected: FAIL，导航类型不存在。

- [ ] **Step 3: 实现密封路由和有界历史**

```java
public sealed interface ShopRoute permits ShopRoute.Home, ShopRoute.Search,
        ShopRoute.Product, ShopRoute.Storefront, ShopRoute.Cart,
        ShopRoute.Checkout, ShopRoute.PaymentResult {
    record Home(HomeProductQuery query) implements ShopRoute { }
    record Search(ProductSearchQuery query) implements ShopRoute { }
    record Product(String productId) implements ShopRoute { }
    record Storefront(String shopId) implements ShopRoute { }
    record Cart() implements ShopRoute { }
    record Checkout() implements ShopRoute { }
    record PaymentResult(PaymentView payment) implements ShopRoute { }
}
```

`open` 在目标等于当前路由时直接返回；否则将当前路由压入 `ArrayDeque`，超过 20 条时移除最旧项，再调用 `host.render(target)`。`back` 弹出最近历史并渲染，但不把离开的页面重新写入历史。

- [ ] **Step 4: 运行导航测试**

Run: `mvn -pl vcampus-client -am -Dtest=ShopNavigatorTest test`

Expected: PASS，查询对象随路由保留，历史最多 20 条。

- [ ] **Step 5: 提交导航**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation
git commit -m "feat(shop): add bounded buyer navigation"
```

### Task 5: 商品首页、搜索、详情与店铺页面

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/async/LatestRequest.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/style/ShopPageState.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/style/ShopUiKit.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/style/DefaultShopUiKit.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductCardsPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ShopHomePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductSearchPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductDetailPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/BuyerShopPanel.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ShopSwingTestSupport.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/style/ShopUiKitTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/CatalogPanelsTest.java`

**Interfaces:**
- Consumes: `ShopClientPort`、`ShopNavigator`、Task 4 路由和商品 DTO。
- Produces: `ShopUiKit`、`DefaultShopUiKit`、`load(...)` 方法、商品/店铺导航事件和 SKU 选择状态，供购物车页面与 UI 安装器使用。

- [ ] **Step 1: 写异步加载、价格显示和过时响应测试**

```java
@Test
void rendersMinimumPriceAndIgnoresOlderSearchCompletion() throws Exception {
    CompletableFuture<PageResult<ProductSummary>> first = new CompletableFuture<>();
    CompletableFuture<PageResult<ProductSummary>> second = new CompletableFuture<>();
    when(client.search(any())).thenReturn(first, second);
    ProductSearchPanel panel = onEdt(() ->
            new ProductSearchPanel(client, navigator, uiKit, sessionExpired));
    ProductSearchQuery oldQuery = new ProductSearchQuery("旧", null, null, null,
            ProductSortMode.SALES_DESC, 0, 20);
    ProductSearchQuery newQuery = new ProductSearchQuery("新", null, null, null,
            ProductSortMode.SALES_DESC, 0, 20);
    onEdt(() -> panel.search(oldQuery));
    onEdt(() -> panel.search(newQuery));
    second.complete(page(new ProductSummary("new", "shop-1", "文具店", "new",
            "文具", new BigDecimal("8.00"), 0, Instant.EPOCH)));
    first.complete(page(new ProductSummary("old", "shop-1", "文具店", "old",
            "文具", new BigDecimal("3.00"), 0, Instant.EPOCH)));
    flushEdt();
    assertThat(panel.visibleProductNames()).containsExactly("new");
    assertThat(panel.visiblePrices()).containsExactly("¥8.00 起");
}
```

另写 `ShopUiKitTest`，读取 `src/main/java/edu/seu/vcampus/client/shop/ui/buyer` 下的 Java 源文件，断言不存在 `java.awt.Color`、`new Font` 或 `BorderFactory`；用记录型 `ShopUiKit` 断言首页搜索按钮调用 `primaryButton`、商品结果调用 `productCard`，加载、空结果、错误和断线调用带对应 `ShopPageState` 的 `stateView`。

```java
String buyerSources;
try (Stream<Path> files = Files.walk(Path.of(
        "src/main/java/edu/seu/vcampus/client/shop/ui/buyer"))) {
    buyerSources = files.filter(path -> path.toString().endsWith(".java"))
            .map(path -> assertDoesNotThrow(() -> Files.readString(path)))
            .collect(Collectors.joining("\n"));
}
assertThat(buyerSources).doesNotContain(
        "java.awt.Color", "new Font", "BorderFactory");
```

测试使用以下 EDT 工具，`component` 递归遍历 `Container.getComponents()` 并按 `Component.getName()` 查找：

```java
public final class ShopSwingTestSupport {
    public static <T> T onEdt(Callable<T> action) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { value.set(action.call()); }
            catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        return value.get();
    }

    public static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    public static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    public static <T extends Component> T component(
            Container root, String name, Class<T> type) {
        T match = componentOrNull(root, name, type);
        if (match != null) return match;
        throw new AssertionError("Missing component: " + name);
    }

    private static <T extends Component> T componentOrNull(
            Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container nested) {
                T match = componentOrNull(nested, name, type);
                if (match != null) return match;
            }
        }
        return null;
    }
}
```

- [ ] **Step 2: 运行目录 UI 测试并确认失败**

Run: `mvn -pl vcampus-client -am -Dtest=CatalogPanelsTest test`

Expected: FAIL，买家目录页面不存在。

- [ ] **Step 3: 实现最新请求守卫和商品卡片**

```java
public final class LatestRequest {
    private final AtomicLong sequence = new AtomicLong();
    private volatile boolean disposed;
    public long begin() { return sequence.incrementAndGet(); }
    public boolean accepts(long id) { return !disposed && sequence.get() == id; }
    public void dispose() { disposed = true; sequence.incrementAndGet(); }
}
```

定义语义 UI 边界：

```java
public enum ShopPageState {
    INITIAL, LOADING, NORMAL, EMPTY, ERROR, DISCONNECTED, SUBMITTING, CONFLICT
}

public interface ShopUiKit {
    JButton navigationButton(String name, String text);
    JButton primaryButton(String name, String text);
    JButton secondaryButton(String name, String text);
    JPanel filterPanel(String name, LayoutManager layout);
    JPanel productCard(String name, LayoutManager layout);
    JComponent stateView(String name, ShopPageState state,
            String message, Runnable retry);
}
```

`DefaultShopUiKit` 只构造标准 `JButton`、`JPanel` 和带可选重试按钮的状态 `JPanel`，设置组件名称并保留 `UIManager` 当前外观：

```java
public final class DefaultShopUiKit implements ShopUiKit {
    public JButton navigationButton(String name, String text) {
        return named(new JButton(text), name);
    }
    public JButton primaryButton(String name, String text) {
        return named(new JButton(text), name);
    }
    public JButton secondaryButton(String name, String text) {
        return named(new JButton(text), name);
    }
    public JPanel filterPanel(String name, LayoutManager layout) {
        return named(new JPanel(layout), name);
    }
    public JPanel productCard(String name, LayoutManager layout) {
        return named(new JPanel(layout), name);
    }
    public JComponent stateView(String name, ShopPageState state,
            String message, Runnable retry) {
        JPanel panel = named(new JPanel(new FlowLayout()), name);
        panel.add(new JLabel(message));
        if (retry != null) {
            JButton button = secondaryButton(name + ".retry", "重试");
            button.addActionListener(event -> retry.run());
            panel.add(button);
        }
        return panel;
    }
    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name);
        return component;
    }
}
```

该类不得调用 `setForeground`、`setBackground`、`setFont`、`setBorder`，不得保存颜色、字体、边距或边框常量。所有目录页面通过构造器接收同一个非空 `ShopUiKit`。

每个 `load/search` 保存 `long request = latest.begin()`，Future 完成后通过 `SwingUtilities.invokeLater` 更新 UI，并先检查 `latest.accepts(request)`。`ProductCardsPanel` 通过 `uiKit.productCard(...)` 创建卡片，显示商品名、店铺名、分类、销量和 `¥<minimumPrice> 起`，点击卡片打开 `new ShopRoute.Product(productId)`。

- [ ] **Step 4: 实现目录页面的准确查询和导航**

`ShopHomePanel` 使用 `HomeProductQuery(null, null, SALES_DESC, 0, 20)`；`ProductSearchPanel` 读取关键字、分类、最低价、最高价和排序；`ProductDetailPanel` 显示 `ProductDetail.skus()` 并将选择限制为 `active && availableQuantity > 0`，调用 `addToCart(new AddCartItemCommand(selectedSkuId, quantity))`，显示返回购物车的商品总数量并提供 `new ShopRoute.Cart()` 导航；店铺按钮打开 `Storefront(shopId)`；`BuyerShopPanel` 先调用 `getShop(shopId)`，再调用带同一 `shopId` 的 `getShopProducts(...)`。

```java
private void finish(long request, PageResult<ProductSummary> result, Throwable failure) {
    SwingUtilities.invokeLater(() -> {
        if (!latest.accepts(request)) return;
        searchButton.setEnabled(true);
        if (failure != null) {
            errors.show(ShopUiErrors.code(failure));
            return;
        }
        cards.showProducts(result.items());
    });
}
```

- [ ] **Step 5: 运行目录 UI 测试**

Run: `mvn -pl vcampus-client -am -Dtest=CatalogPanelsTest,ShopUiKitTest,ShopNavigatorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS，筛选、排序、路由参数、EDT 更新和过时响应保护均通过。

- [ ] **Step 6: 提交目录页面**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/async vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/style vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ShopSwingTestSupport.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/style/ShopUiKitTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/CatalogPanelsTest.java
git commit -m "feat(shop): add buyer catalog pages"
```

### Task 6: 购物车、结算、收银台与支付结果

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CartPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CheckoutPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/SimulatedCashierDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/PaymentResultPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopUiErrors.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopDialogs.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/PurchasePanelsTest.java`

**Interfaces:**
- Consumes: `ShopClientPort` 的购物车、结算和支付方法、`ShopNavigator` 和 Task 5 `ShopUiKit`。
- Produces: 可完成 `Cart -> Checkout -> PaymentResult` 的异步页面链路。

- [ ] **Step 1: 写购物车保留、价格确认和支付重试测试**

```java
@Test
void priceChangeKeepsCartAndRetriesOnlyAfterConfirmation() throws Exception {
    CartView cart = cartView();
    when(client.getCart()).thenReturn(CompletableFuture.completedFuture(cart));
    when(client.checkout(any()))
            .thenReturn(CompletableFuture.failedFuture(
                    new ShopClientException("SHOP_PRICE_CHANGED")))
            .thenReturn(CompletableFuture.completedFuture(checkoutResult()));
    CheckoutPanel panel = onEdt(() ->
            new CheckoutPanel(client, navigator, uiKit, dialogs, sessionExpired));
    onEdt(panel::load);
    flushEdt();
    onEdt(panel::submit);
    flushEdt();
    assertThat(panel.visibleItems()).hasSize(cart.items().size());
    assertThat(dialogs.confirmedCode()).isEqualTo("SHOP_PRICE_CHANGED");
    onEdt(panel::confirmLatestPriceAndRetry);
    verify(client).checkout(argThat(CheckoutCommand::acceptLatestPrice));
}

@Test
void failedPaymentRemainsRetryableAndSuccessNavigatesToResult() throws Exception {
    when(client.simulatePayment(any()))
            .thenReturn(completedFuture(pendingPayment()))
            .thenReturn(completedFuture(successfulPayment()));
    SimulatedCashierDialog cashier = onEdt(() -> new SimulatedCashierDialog(
            null, client, navigator, uiKit, checkoutResult(), sessionExpired));
    onEdt(() -> cashier.submit(PaymentChannel.ALIPAY, PaymentAttemptStatus.FAILED));
    flushEdt();
    assertThat(cashier.retryEnabled()).isTrue();
    onEdt(() -> cashier.submit(PaymentChannel.ALIPAY, PaymentAttemptStatus.SUCCEEDED));
    flushEdt();
    verify(navigator).open(new ShopRoute.PaymentResult(successfulPayment()));
}
```

`dialogs` 是测试内的 `RecordingDialogs`，实现以下生产接口并保存最后一次错误码与确认回调：

```java
public interface ShopDialogs {
    void showError(String code);
    void confirm(String code, Runnable accepted);
}
```

`CheckoutPanel` 收到 `SHOP_PRICE_CHANGED` 时调用
`dialogs.confirm("SHOP_PRICE_CHANGED", this::confirmLatestPriceAndRetry)`；测试调用保存的回调来验证 `acceptLatestPrice=true` 的第二次请求。

- [ ] **Step 2: 运行购买 UI 测试并确认失败**

Run: `mvn -pl vcampus-client -am -Dtest=PurchasePanelsTest test`

Expected: FAIL，购买页面不存在。

- [ ] **Step 3: 实现购物车和结算命令构造**

四个购买页面通过构造器接收 Task 5 的同一个 `ShopUiKit`，按钮、筛选/摘要区和页面状态只通过该接口创建。发起写操作时显示 `SUBMITTING` 并禁用对应按钮；完成后恢复 `NORMAL` 或稳定错误状态。`CONFLICT` 由 UI 边界支持，但当前买家协议未暴露稳定并发冲突错误码，因此本计划不把任意错误伪装成冲突。`CartPanel` 对每行使用 `UpdateCartItemCommand(cartItemId, quantity, rowVersion)`，删除使用 `removeCartItem(cartItemId)`；每次成功用返回的 `CartView` 整体重绘。`CheckoutPanel` 从当前购物车构造：

```java
private CheckoutCommand command(boolean acceptLatestPrice) {
    return new CheckoutCommand(cart.items().stream()
            .map(item -> new CheckoutItem(
                    item.cartItemId(), item.displayedUnitPrice()))
            .toList(), acceptLatestPrice);
}
```

库存不足和价格变化都保留当前视图；只有价格变化出现“按最新价格重新结算”操作。

- [ ] **Step 4: 实现模拟收银台和结果页**

收银台只展示支付单号、金额、到期时间、渠道选择和成功/失败/取消按钮。请求为：

```java
new SimulatePaymentCommand(checkout.paymentId(), selectedChannel, selectedResult)
```

`PaymentStatus.PENDING` 保持对话框可重试；`SUCCEEDED/CANCELLED/EXPIRED` 关闭收银台并打开 `PaymentResult`。结果页显示 `paymentNumber`、`amount`、`successfulChannel` 和 `status`，并提供返回首页和查看空购物车按钮。

- [ ] **Step 5: 运行购买 UI 测试**

Run: `mvn -pl vcampus-client -am -Dtest=PurchasePanelsTest,ShopClientServiceTest test`

Expected: PASS，按钮忙碌状态、错误保留、价格确认、失败重试和成功导航均通过。

- [ ] **Step 6: 提交购买页面**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/PurchasePanelsTest.java
git commit -m "feat(shop): add buyer purchase pages"
```

### Task 7: Shop UI 安装器与功能分支验收

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/style/ShopUiKit.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/style/DefaultShopUiKit.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopUiInstaller.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/style/ShopUiKitTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`

**Interfaces:**
- Consumes: `MainFrame.navigation()`、`MainFrame.pageNavigator()`、Task 3–6 客户端与页面，以及 Task 5 `ShopUiKit`。
- Produces: `ShopUiInstaller.install(MainFrame, ShopClientPort, ShopUiKit, Runnable sessionExpired)`，供 Demo 客户端入口调用。

- [ ] **Step 1: 写固定页面注册和商城入口测试**

```java
@Test
void installsOneShopEntryAndRendersHomeWithoutChangingMainFrame() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    MainFrame frame = onEdt(MainFrame::new);
    when(client.home(any())).thenReturn(completedFuture(page(productSummary())));
    onEdt(() -> ShopUiInstaller.install(
            frame, client, new DefaultShopUiKit(), sessionExpired));
    JButton shop = component(frame.navigation(), "shop.navigation", JButton.class);
    onEdt(shop::doClick);
    flushEdt();
    assertThat(shop.getText()).isEqualTo("校园商城");
    assertThat(component(frame.content(), "shop.home", JPanel.class)).isVisible();
}
```

- [ ] **Step 2: 运行安装器测试并确认失败**

Run: `mvn -pl vcampus-client -am -Dtest=ShopUiTest test`

Expected: FAIL，安装器与页面协调器不存在。

- [ ] **Step 3: 实现固定 CardLayout 页面协调器**

`ShopPageCoordinator` 在构造时把同一个非空 `ShopUiKit` 注入所有 Shop 页面，并且只注册以下稳定页面 ID：

```text
shop.home
shop.search
shop.product
shop.storefront
shop.cart
shop.checkout
shop.payment-result
```

其 `render(ShopRoute route)` 使用模式匹配调用目标页面的 `load(...)`，然后调用共享 `PageNavigator.show(pageId)`。`ShopUiInstaller` 通过 `uiKit.navigationButton("shop.navigation", "校园商城")` 创建商城入口，并将点击事件映射到默认 `HomeProductQuery(null, null, SALES_DESC, 0, 20)`。安装器不得创建或修改公共视觉令牌。

- [ ] **Step 4: 运行 Shop 功能分支完整验证**

Run: `mvn -pl vcampus-common,vcampus-server,vcampus-client -am verify`

Expected: PASS，既有 42 个 Shop 测试与新增 Handler、客户端、导航和 UI 测试全部通过。

Run: `git diff --check`

Expected: 无输出。

- [ ] **Step 5: 提交 UI 组合层**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/style/ShopUiKit.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/style/DefaultShopUiKit.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopUiInstaller.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/style/ShopUiKitTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java
git commit -m "feat(shop): assemble authenticated buyer UI"
```

### Task 8: 建立 Demo 集成 worktree 与真实会话适配器

**Files:**
- Create in integration worktree: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/adapter/FoundationShopUserAdapter.java`
- Test in integration worktree: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/adapter/FoundationShopUserAdapterTest.java`

**Interfaces:**
- Consumes: 用户分支的 `AuthorizationPort`、`UserIdentity`、`UserRole`，以及 Shop 的 `ShopUserPort` 和 `ShopAccessException`。
- Produces: `FoundationShopUserAdapter(AuthorizationPort)`，把真实 Session 投影为 Shop 身份。

- [ ] **Step 1: 使用 worktree 技能创建集成环境**

执行本任务前调用 `superpowers:using-git-worktrees`。在主仓库确认目标路径后运行：

```powershell
git fetch origin feat/user-management
git worktree add -b demo/shop-auth "E:\summer-school\vCampus\.worktrees\shop-auth-demo" origin/feat/user-management
Set-Location -LiteralPath "E:\summer-school\vCampus\.worktrees\shop-auth-demo"
git cherry-pick 9fd1996^..feat/shop-only
```

Expected: 新 worktree 位于 `demo/shop-auth`，`feat/shop-only` 保持独立且工作区干净。若远端用户分支在计划后更新，先记录新 SHA 并重新验证公开契约，再继续 cherry-pick。

- [ ] **Step 2: 写正常、受限和过期会话适配测试**

```java
@Test
void mapsStudentAndRejectsRestrictedSession() {
    when(authorization.requireSession("student-token"))
            .thenReturn(new UserIdentity("buyer-1", "DEMO_BUYER",
                    UserRole.STUDENT, Set.of(), false));
    assertThat(adapter.requireUser("student-token"))
            .isEqualTo(new ShopUser("buyer-1", ShopUserKind.STUDENT, true));

    when(authorization.requireSession("restricted-token"))
            .thenReturn(new UserIdentity("buyer-2", "FIRST_LOGIN",
                    UserRole.STUDENT, Set.of(), true));
    assertThatThrownBy(() -> adapter.requireUser("restricted-token"))
            .isInstanceOf(ShopAccessException.class)
            .hasMessage("AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED");
}
```

- [ ] **Step 3: 运行适配器测试并确认失败**

Run: `mvn -pl vcampus-server -am -Dtest=FoundationShopUserAdapterTest test`

Expected: FAIL，适配器不存在。

- [ ] **Step 4: 实现用户身份投影**

```java
public ShopUser requireUser(String sessionToken) {
    try {
        UserIdentity identity = authorization.requireSession(sessionToken);
        if (identity.restricted()) {
            throw new ShopAccessException("AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED");
        }
        ShopUserKind kind = switch (identity.role()) {
            case STUDENT -> ShopUserKind.STUDENT;
            case TEACHER -> ShopUserKind.TEACHER;
            case ADMIN -> ShopUserKind.ADMINISTRATOR;
        };
        return new ShopUser(identity.userId(), kind, true);
    } catch (SessionExpiredException error) {
        throw new ShopAccessException("AUTH_SESSION_EXPIRED");
    }
}
```

本阶段的 `requireAdministrator()` 返回 `AUTH_FORBIDDEN`，因为买家 Demo 不建立管理员操作上下文；Task 6 管理员接线时将根据届时的 Handler 会话参数扩展该适配方式。

- [ ] **Step 5: 运行适配器与 Handler 测试并提交**

Run: `mvn -pl vcampus-server -am -Dtest=FoundationShopUserAdapterTest,BuyerShopHandlersTest test`

Expected: PASS。

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/shop/adapter vcampus-server/src/test/java/edu/seu/vcampus/server/shop/adapter
git commit -m "feat(shop-demo): adapt authenticated users"
```

### Task 9: 本地登录购物数据库与组合服务端

**Files:**
- Create in integration worktree: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabase.java`
- Create in integration worktree: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoRuntime.java`
- Create in integration worktree: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoServerMain.java`
- Test in integration worktree: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabaseTest.java`

**Interfaces:**
- Consumes: `001_common.sql`、`010_user.sql`、`010_roles_permissions.sql`、`050_shop.sql`，以及用户和 Shop 现有服务构造器。
- Produces: `ShopAuthDemoDatabase.initialize(Path database, Path schemaDir, Path seedDir)`；`ShopAuthDemoRuntime implements AutoCloseable` 并提供 `static start(Path database, int port)`、`localPort()` 和 `close()`。

- [ ] **Step 1: 写可重复初始化和登录数据测试**

```java
@Test
void createsKnownBuyerAndTwoVisibleShops() throws Exception {
    Path database = temp.resolve("shop-auth.accdb");
    ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());
    try (Connection connection = DriverManager.getConnection(
            "jdbc:ucanaccess://" + database)) {
        assertThat(count(connection,
                "SELECT COUNT(*) FROM tblUser WHERE loginId='DEMO_BUYER' "
                        + "AND accountStatus='ACTIVE' AND mustChangePassword=FALSE"))
                .isEqualTo(1);
        assertThat(count(connection,
                "SELECT COUNT(*) FROM tblShop WHERE shopStatus='ACTIVE'"))
                .isEqualTo(2);
    }
}
```

- [ ] **Step 2: 运行数据库测试并确认失败**

Run: `mvn -pl vcampus-server -am -Dtest=ShopAuthDemoDatabaseTest test`

Expected: FAIL，初始化器不存在。

- [ ] **Step 3: 实现固定 Demo 用户和商品种子**

执行三个 Schema 和角色权限 Seed 后，插入买家时使用与 PBKDF2-HMAC-SHA256/120000 次兼容的固定测试值：

```sql
INSERT INTO tblUser
 (userId, loginId, passwordHash, passwordSalt, passwordIterations,
  roleCode, accountStatus, mustChangePassword, failedLoginCount,
  rowVersion, createdAt, updatedAt)
VALUES
 ('demo-buyer', 'DEMO_BUYER',
  '7FUgpmUKRTM7k5BqyJmwQrxgmA/3uSQ3C8yhryadIAA=',
  'AAECAwQFBgcICQoLDA0ODw==', 120000,
  'STUDENT', 'ACTIVE', FALSE, 0, 0, NOW(), NOW())
```

复用现有 `ShopDemo` 的店铺/商品/SKU 插入结构，使用 `demo-shop-stationery`、`demo-shop-books`、`demo-pen-black` 和 `demo-book-standard` 固定 ID；额外加入库存为 1 的低库存 SKU。

- [ ] **Step 4: 实现组合运行时和 ServerMain**

`ShopAuthDemoRuntime` 创建一个共享 `ConnectionProvider`、`TransactionManager`、`StripedResourceLockManager`、`SessionRegistry` 和 `Clock`。在同一 Router 上注册：

```java
new UserHandlers(router, userService, authorization);
new BuyerShopHandlers(router, shopUsers, deduplicator, shopService,
        cartService, checkoutService, paymentService, businessLogger);
```

`ShopAuthDemoServerMain` 默认数据库路径为 `vcampus-database/demo/vcampus-shop-auth-demo.accdb`，默认端口为 `19090`；启动前调用初始化器并打印数据库绝对路径、端口和 Demo 登录名。

- [ ] **Step 5: 运行数据库测试和服务端模块测试**

Run: `mvn -pl vcampus-server -am -Dtest=ShopAuthDemoDatabaseTest,FoundationShopUserAdapterTest,BuyerShopHandlersTest test`

Expected: PASS，生成数据库包含真实用户 Schema、两个店铺和完整 Shop 表。

- [ ] **Step 6: 提交数据库和服务端 Demo**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo
git commit -m "feat(shop-demo): add local authenticated runtime"
```

### Task 10: Demo 客户端、Socket 端到端验收和启动说明

**Files:**
- Create in integration worktree: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMain.java`
- Create in integration worktree: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthEndToEndTest.java`
- Create in integration worktree: `vcampus-database/demo/SHOP_AUTH_DEMO.md`
- Create in integration worktree: `vcampus-distribution/scripts/start-shop-auth-demo-server.ps1`
- Create in integration worktree: `vcampus-distribution/scripts/start-shop-auth-demo-client.ps1`

**Interfaces:**
- Consumes: 用户模块 `LoginFrame/UserClientService`、Task 3 `ShopClientService`、Task 7 `ShopUiInstaller` 和 Task 9 组合服务端。
- Produces: 双 PowerShell 启动流程，以及从真实登录到支付成功的 Socket 验收证据。

- [ ] **Step 1: 写真实 Socket 端到端测试**

```java
@Test
void loginCartCheckoutAndPaymentPersistExactlyOnce() throws Exception {
    Path database = temp.resolve("shop-auth-e2e.accdb");
    ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());
    try (ShopAuthDemoRuntime runtime = ShopAuthDemoRuntime.start(database, 0);
         ClientConnection connection = new ClientConnection(
                 "127.0.0.1", runtime.localPort())) {
        connection.connect(Duration.ofSeconds(5));
        UserClientService users = new UserClientService(
                connection, "e2e-client", Duration.ofSeconds(5));
        users.login("DEMO_BUYER", "DemoPassword7".toCharArray()).join();
        ShopClientService shop = new ShopClientService(connection, Duration.ofSeconds(5));
        CartView cart = shop.addToCart(
                new AddCartItemCommand("demo-pen-black", 2)).join();
        CheckoutResult checkout = shop.checkout(new CheckoutCommand(
                cart.items().stream().map(i -> new CheckoutItem(
                        i.cartItemId(), i.displayedUnitPrice())).toList(), false)).join();
        PaymentView payment = shop.simulatePayment(new SimulatePaymentCommand(
                checkout.paymentId(), PaymentChannel.ALIPAY,
                PaymentAttemptStatus.SUCCEEDED)).join();
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertDatabaseInvariant(database, checkout, payment);
    }
}
```

`assertDatabaseInvariant` 必须断言：购物车项为 0、支付尝试为 1、支付状态为 `SUCCEEDED`、预留状态为 `CONSUMED`、SKU 库存减少 2、`reservedQuantity=0`、商品 `salesCount` 增加 2。再用相同 `requestId` 的底层 `Message` 重放支付命令，断言库存和销量保持不变。

- [ ] **Step 2: 运行 E2E 测试并确认缺少组合入口**

Run: `mvn -pl vcampus-client -am -Dtest=ShopAuthEndToEndTest test`

Expected: FAIL，Demo 客户端/运行时组合尚未完成。

- [ ] **Step 3: 实现登录后安装 Shop 的客户端入口**

```java
private static void showLogin(UserClientService users, ShopClientService shop,
        ClientConnection connection) {
    LoginFrame login = new LoginFrame(users,
            result -> showMain(result, users, shop, connection));
    login.setVisible(true);
}

private static void showMain(LoginResult result, UserClientService users,
        ShopClientService shop, ClientConnection connection) {
    MainFrame main = new MainFrame(result.user());
    ShopUiInstaller.install(main, shop, new DefaultShopUiKit(), () -> {
        main.dispose();
        connection.setSessionToken(null);
        showLogin(users, shop, connection);
    });
    main.setVisible(true);
}
```

启动时在 EDT 调用 `showLogin(users, shop, connection)`；会话过期后清空本地令牌并通过同一方法重新登录，从而重新安装完整 Shop UI。密码校验和会话签发继续调用用户模块。

- [ ] **Step 4: 添加 PowerShell 启动脚本和人工步骤**

服务端脚本执行：

```powershell
mvn -pl vcampus-server -am package
java -Dlogback.configurationFile=vcampus-distribution/config/logback.xml -cp vcampus-distribution/lib/vCampusServer.jar edu.seu.vcampus.server.shop.demo.ShopAuthDemoServerMain
```

客户端脚本执行：

```powershell
mvn -pl vcampus-client -am package
java -Dlogback.configurationFile=vcampus-distribution/config/logback.xml -cp vcampus-distribution/lib/vCampusClient.jar edu.seu.vcampus.client.shop.demo.ShopAuthDemoClientMain
```

`SHOP_AUTH_DEMO.md` 写明两个终端的顺序、`DEMO_BUYER / DemoPassword7`、数据库路径、`logs/server.log`、`logs/business.log`，以及首页、详情、购物车、结算、支付宝成功和数据库检查清单。

- [ ] **Step 5: 运行完整自动验证**

Run: `mvn -pl vcampus-common,vcampus-server,vcampus-client -am verify`

Expected: PASS，包括用户登录测试、既有 Shop 测试、新增 Handler/UI 测试和 `ShopAuthEndToEndTest`。

Run: `git diff --check`

Expected: 无输出。

- [ ] **Step 6: 人工运行双终端 Demo**

在两个新 PowerShell 中分别运行：

```powershell
Set-Location -LiteralPath "E:\summer-school\vCampus\.worktrees\shop-auth-demo"
.\vcampus-distribution\scripts\start-shop-auth-demo-server.ps1
```

```powershell
Set-Location -LiteralPath "E:\summer-school\vCampus\.worktrees\shop-auth-demo"
.\vcampus-distribution\scripts\start-shop-auth-demo-client.ps1
```

Expected: 可登录、可完成购物、`business.log` 出现 `SHOP_CHECKOUT` 和 `PAYMENT` 事件，数据库状态满足 Step 1 的不变量。

- [ ] **Step 7: 提交完整 Demo**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/shop/demo vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo vcampus-database/demo/SHOP_AUTH_DEMO.md vcampus-distribution/scripts/start-shop-auth-demo-server.ps1 vcampus-distribution/scripts/start-shop-auth-demo-client.ps1
git commit -m "feat(shop-demo): complete authenticated purchase flow"
```

### Task 11: 最终分支与交付审计

**Files:**
- Verify only; no planned source edits.

**Interfaces:**
- Consumes: `feat/shop-only` 与 `demo/shop-auth` 的所有提交和测试结果。
- Produces: 可复核的 Shop-only 功能分支、隔离 Demo 分支、提交清单和运行说明。

- [ ] **Step 1: 验证两个 worktree 状态**

```powershell
git -C "E:\summer-school\vCampus\.worktrees\shop-only" status --short --branch
git -C "E:\summer-school\vCampus\.worktrees\shop-auth-demo" status --short --branch
git worktree list
```

Expected: 两个 worktree 都干净，分别位于 `feat/shop-only` 和 `demo/shop-auth`。

- [ ] **Step 2: 审计正式分支修改范围**

```powershell
git -C "E:\summer-school\vCampus\.worktrees\shop-only" diff --name-only origin/feat/shop-only..feat/shop-only
```

Expected: 只有 Shop 包、Shop 测试、Shop 设计/计划和 `docs/ui-review/manifest.md`。

- [ ] **Step 3: 记录测试和提交证据**

```powershell
git -C "E:\summer-school\vCampus\.worktrees\shop-only" log --oneline origin/feat/shop-only..feat/shop-only
git -C "E:\summer-school\vCampus\.worktrees\shop-auth-demo" log --oneline origin/feat/user-management..demo/shop-auth
```

Expected: 功能提交与 Demo 集成提交分组清晰；交付报告列出 Maven `verify` 结果、Demo 登录账号、数据库路径和日志路径。
