# Virtual Campus Multi-Merchant Shop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a campus multi-merchant marketplace with seller approval, products/SKUs, explainable recommendations, persisted carts, cross-shop checkout, inventory reservation, retryable simulated payments, order fulfillment, administration, and sixteen Swing pages.

**Architecture:** Buyer, seller, and admin services share repositories but expose separate interfaces and permissions. Checkout locks sorted SKU keys and creates one order group with per-shop orders; payment attempts are append-only while the aggregate payment owns the final state and inventory transition.

**Tech Stack:** JDK 21, Maven, Swing, UCanAccess, BigDecimal, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-08-24-vcampus-shop-module-design.md` and the overall architecture spec.

## Global Constraints

- Complete foundation and user plans first.
- Seller capability is derived from an approved active shop; never mutate the user's base role.
- Never collect, persist, or log real payment accounts, card numbers, passwords, or verification codes.
- Money uses `BigDecimal`; never calculate price or amount with `double`.
- Lock all affected SKU IDs in sorted order for checkout/payment.
- A failed payment attempt leaves the aggregate payment `PENDING`; only success, explicit cancellation, or expiry releases/consumes the reservation.
- Do not implement refunds or real payment-network calls.

---

### Task 1: Shop Schema, Seller Application, and Approval

**Files:**
- Create: `vcampus-database/schema/050_shop.sql`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/ShopRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/AccessShopRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/SellerApplicationService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/SellerService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ShopAdminService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/SellerApplicationServiceTest.java`

**Interfaces:**
- Consumes: `UserQueryPort`, authorization, transactions, locks.
- Produces: `apply`, `searchApplications`, `reviewApplication`, `suspendShop`, and `requireOwnedActiveShop`.

- [ ] **Step 1: Write eligibility, one-shop, and double-review tests**

```java
@Test
void approvingTwiceCreatesExactlyOneShop() throws Exception {
    String applicationId = seedPendingApplication("user-1");
    List<Outcome<SellerApplicationView>> outcomes = concurrently(2,
            () -> service.reviewApplication(approve(applicationId, 0)));
    assertThat(outcomes.stream().filter(Outcome::isSuccess)).hasSize(1);
    assertThat(shops.countByOwner("user-1")).isEqualTo(1);
}
```

- [ ] **Step 2: Run seller tests**

Run: `mvn -pl vcampus-server -am -Dtest=SellerApplicationServiceTest test`

Expected: FAIL because schema/service are absent.

- [ ] **Step 3: Implement all thirteen shop tables and seller workflow**

```java
return locks.withLocks(sorted(key("SELLER_APPLICATION", applicationId),
        key("USER", applicantId)), () -> transactions.inTransaction(c -> {
    SellerApplication pending = applications.requirePending(c, applicationId, version);
    shops.requireNoActiveShop(c, pending.applicantUserId());
    Shop shop = shops.insert(c, Shop.approvedFrom(pending));
    applications.markApproved(c, pending, reviewerId, clock.instant());
    return mapper.toView(pending, shop);
}));
```

- [ ] **Step 4: Run seller integration/concurrency tests**

Run: `mvn -pl vcampus-server -am -Dtest=SellerApplicationServiceTest,ShopOwnershipTest test`

Expected: PASS for teacher/student eligibility, inactive account rejection, resubmission, one active shop, ownership, and suspension.

- [ ] **Step 5: Commit seller foundation**

```bash
git add vcampus-database/schema/050_shop.sql vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test
git commit -m "feat(shop): add seller approval and shop persistence"
```

### Task 2: Products, SKUs, Search, and Recommendations

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductSummary.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/HomeProductQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductSearchQuery.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ProductService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ShopService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/recommendation/RecommendationService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/recommendation/RecommendationServiceTest.java`

**Interfaces:**
- Consumes: owned active shop and product behavior repositories.
- Produces: product/SKU CRUD, search, detail, `getHomeProducts` fallback behavior.

- [ ] **Step 1: Write scoring, filtering, and fallback tests**

```java
@Test
void filtersUnavailableProductsAndFallsBackWhenPersonalizationFails() {
    seed(activeInStock("eligible"), inactive("hidden"), zeroStock("empty"));
    recommendationSource.failWith(new SQLException("simulated"));
    assertThat(service.getHomeProducts(queryFor("user-1")).items())
            .extracting(ProductSummary::productId)
            .contains("eligible")
            .doesNotContain("hidden", "empty");
}
```

- [ ] **Step 2: Run product/recommendation tests**

Run: `mvn -pl vcampus-server -am -Dtest=RecommendationServiceTest,ProductServiceTest test`

Expected: FAIL before services exist.

- [ ] **Step 3: Implement ownership checks and deterministic recommendation score**

```java
BigDecimal score = affinity.multiply(new BigDecimal("0.50"))
        .add(salesRank.multiply(new BigDecimal("0.30")))
        .add(freshness.multiply(new BigDecimal("0.20")));
```

Sort by score descending then `productId` ascending for deterministic tests. On recommendation failure, return active in-stock products sorted by 30-day sales and creation time.

- [ ] **Step 4: Run product tests**

Run: `mvn -pl vcampus-server -am -Dtest=RecommendationServiceTest,ProductServiceTest,ShopOwnershipTest test`

Expected: PASS for owner-only mutation, price/stock validation, hidden inventory, scoring, and fallback.

- [ ] **Step 5: Commit products/recommendations**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test
git commit -m "feat(shop): add products search and recommendations"
```

### Task 3: Persisted Shopping Cart

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/AddCartItemCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/UpdateCartItemCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/CartView.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/CartService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/CartServiceTest.java`

**Interfaces:**
- Consumes: product/SKU repository, authorization, cart lock.
- Produces: `getCart`, `addToCart`, `updateCartItem`, `removeCartItem`.

- [ ] **Step 1: Write merge, persistence, and stale-version tests**

```java
@Test
void repeatedSkuAddsMergeAndSurviveNewSession() {
    service.addToCart(token, new AddCartItemCommand("sku-1", 2));
    service.addToCart(token, new AddCartItemCommand("sku-1", 3));
    CartView restored = new CartService(repositories, newSessionForSameUser()).getCart(token2);
    assertThat(restored.items()).singleElement()
            .extracting(CartItemView::quantity).isEqualTo(5);
}
```

- [ ] **Step 2: Run cart tests**

Run: `mvn -pl vcampus-server -am -Dtest=CartServiceTest test`

Expected: FAIL until cart service exists.

- [ ] **Step 3: Implement `CART:<userId>` locked operations**

```java
return locks.withLock("CART", userId, () -> transactions.inTransaction(c -> {
    ProductSku sku = products.requireActiveSku(c, command.skuId());
    Cart cart = carts.findOrCreate(c, userId);
    carts.mergeItem(c, cart.cartId(), sku.skuId(), command.quantity());
    return mapper.toView(carts.load(c, cart.cartId()));
}));
```

- [ ] **Step 4: Run cart verification**

Run: `mvn -pl vcampus-server -am -Dtest=CartServiceTest,ConcurrentCartUpdateTest test`

Expected: PASS for merge, quantity validation, inactive SKU, ownership, stale version, and concurrent updates.

- [ ] **Step 5: Commit cart behavior**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test
git commit -m "feat(shop): add persisted shopping cart"
```

### Task 4: Cross-Shop Checkout and Inventory Reservation

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/CheckoutCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/CheckoutItem.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/CheckoutResult.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/CheckoutService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/CheckoutServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ConcurrentCheckoutTest.java`

**Interfaces:**
- Consumes: cart/product/order/payment/reservation repositories, locks, transactions.
- Produces: one order group, one order per shop, one payment, and reservations.

- [ ] **Step 1: Write cross-shop, price-change, and stock-race tests**

```java
@Test
void fiveUnitsCannotBeOversoldByTwentyCheckouts() throws Exception {
    seedSku("sku-1", 5, 0, money("10.00"));
    List<Outcome<CheckoutResult>> outcomes = concurrentlyWithDistinctBuyers(20,
            token -> checkout.checkout(token, checkout("sku-1", 1, "10.00", true)));
    assertThat(outcomes.stream().filter(Outcome::isSuccess)).hasSize(5);
    assertThat(skus.require("sku-1").reservedQuantity()).isEqualTo(5);
}
```

- [ ] **Step 2: Run checkout tests**

Run: `mvn -pl vcampus-server -am -Dtest=CheckoutServiceTest,ConcurrentCheckoutTest test`

Expected: FAIL before checkout exists.

- [ ] **Step 3: Implement sorted SKU locking and all-or-nothing order creation**

```java
List<ResourceKey> skuKeys = items.stream().map(CheckoutItem::skuId)
        .distinct().sorted().map(id -> key("SKU", id)).toList();
return locks.withLocks(skuKeys, () -> transactions.inTransaction(c ->
        createOrderGroupOrdersPaymentAndReservations(c, buyerId, command)));
```

If displayed price differs and `acceptLatestPrice=false`, throw `SHOP_PRICE_CHANGED` with latest prices and create no rows. Calculate every line and total with `BigDecimal` and compare totals before commit.

- [ ] **Step 4: Run checkout and conservation tests**

Run: `mvn -pl vcampus-server -am -Dtest=CheckoutServiceTest,ConcurrentCheckoutTest,OrderAmountInvariantTest test`

Expected: PASS for cross-shop split, snapshot fields, no partial orders, five-unit race, and amount equality.

- [ ] **Step 5: Commit checkout**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test
git commit -m "feat(shop): add cross-shop checkout and reservations"
```

### Task 5: Retryable Simulated Payment and Expiry Recovery

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/PaymentChannel.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/PaymentStatus.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/PaymentAttemptStatus.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SimulatePaymentCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/PaymentView.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/payment/SimulatedPaymentService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/payment/ReservationExpiryJob.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/payment/SimulatedPaymentServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/payment/PaymentExpiryRaceTest.java`

**Interfaces:**
- Consumes: payment/order/SKU/reservation repositories.
- Produces: retryable attempts, aggregate terminal payment, inventory consume/release, restart recovery.

- [ ] **Step 1: Write failure-retry, duplicate-success, and expiry-race tests**

```java
@Test
void failedAttemptCanRetryAndDuplicateSuccessConsumesOnce() {
    Payment payment = seedPendingPaymentWithReservation("sku-1", 2);
    service.simulate(token, command(payment, ALIPAY, FAILED));
    assertThat(payments.require(payment.id()).status()).isEqualTo(PENDING);
    service.simulate(token, command(payment, WECHAT, SUCCEEDED));
    service.simulate(token, command(payment, WECHAT, SUCCEEDED));
    assertThat(skus.require("sku-1")).extracting(Sku::stockQuantity,
            Sku::reservedQuantity).containsExactly(8L, 0L);
}
```

- [ ] **Step 2: Run payment tests**

Run: `mvn -pl vcampus-server -am -Dtest=SimulatedPaymentServiceTest,PaymentExpiryRaceTest test`

Expected: FAIL before payment state machine exists.

- [ ] **Step 3: Implement aggregate payment lock and append-only attempts**

```java
return locks.withLocks(paymentAndSortedSkuKeys(paymentId), () ->
        transactions.inTransaction(c -> {
            Payment payment = payments.require(c, paymentId);
            if (payment.status() != PENDING) return mapper.toView(payment);
            attempts.insert(c, PaymentAttempt.complete(command));
            return switch (command.simulatedResult()) {
                case FAILED -> mapper.toView(payment);
                case SUCCEEDED -> completeAndConsume(c, payment, command.channel());
                case CANCELLED -> cancelAndRelease(c, payment);
                case STARTED -> throw new ValidationException("STARTED is not a result");
            };
        }));
```

The expiry job uses the same payment/SKU locks. On startup it processes every `PENDING` payment whose reservation expired; rerunning changes zero additional rows.

- [ ] **Step 4: Run payment and recovery verification**

Run: `mvn -pl vcampus-server -am -Dtest=SimulatedPaymentServiceTest,PaymentExpiryRaceTest,ReservationRecoveryTest test`

Expected: PASS for retry, idempotent success, explicit cancel, expiry, race uniqueness, and inventory conservation.

- [ ] **Step 5: Commit payment state machine**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-server/src/main/java/edu/seu/vcampus/server/shop/payment vcampus-server/src/test
git commit -m "feat(shop): add retryable simulated payments"
```

### Task 6: Order Fulfillment and Platform Governance

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/OrderService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ShopAdminServiceImpl.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/OrderLifecycleTest.java`

**Interfaces:**
- Consumes: paid orders, seller ownership, admin permission.
- Produces: buyer list/cancel/confirm, seller list/status update, platform order/payment searches.

- [ ] **Step 1: Write state-transition and ownership tests**

```java
@ParameterizedTest
@CsvSource({"PAID,PREPARING,true", "PREPARING,SHIPPED,true",
            "SHIPPED,COMPLETED,false", "PENDING_PAYMENT,SHIPPED,false"})
void sellerTransitionsFollowStateMachine(OrderStatus from, OrderStatus to,
                                         boolean allowed) {
    if (allowed) assertThatCode(() -> orders.updateSellerStatus(owner, from, to))
            .doesNotThrowAnyException();
    else assertThatThrownBy(() -> orders.updateSellerStatus(owner, from, to))
            .isInstanceOf(OrderStatusInvalidException.class);
}
```

- [ ] **Step 2: Run lifecycle tests**

Run: `mvn -pl vcampus-server -am -Dtest=OrderLifecycleTest test`

Expected: FAIL before order services exist.

- [ ] **Step 3: Implement explicit transition guards**

```java
private static final Map<OrderStatus, Set<OrderStatus>> SELLER_TRANSITIONS = Map.of(
        PAID, Set.of(PREPARING), PREPARING, Set.of(SHIPPED));
```

Buyers alone confirm `SHIPPED → COMPLETED`; only `PENDING_PAYMENT` groups can be cancelled. Admin search is read-only; suspension hides catalog entries but preserves orders.

- [ ] **Step 4: Run lifecycle/authorization tests**

Run: `mvn -pl vcampus-server -am -Dtest=OrderLifecycleTest,ShopAdminAuthorizationTest test`

Expected: PASS for buyer/seller ownership, transitions, cancellation, completion, and admin queries.

- [ ] **Step 5: Commit lifecycle behavior**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service vcampus-server/src/test
git commit -m "feat(shop): add order fulfillment and governance"
```

### Task 7: Message Handlers, Sixteen Swing Pages, and Acceptance

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/BuyerShopHandlers.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/SellerShopHandlers.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/AdminShopHandlers.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientService.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ShopHomePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductSearchPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductDetailPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CartPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CheckoutPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/SimulatedCashierDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/MyOrdersPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/OrderDetailPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SellerApplicationPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SellerDashboardPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ShopProfilePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ProductManagementPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SellerOrderPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/SellerReviewPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ShopGovernancePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/PlatformOrderPanel.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler/ShopHandlersTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ShopUiTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/ShopEndToEndTest.java`

**Interfaces:**
- Consumes: all shop services, router, authorization, async client.
- Produces: complete buyer/seller/admin message and UI surface.

- [ ] **Step 1: Write cashier privacy and cross-shop E2E tests**

```java
@Test
void cashierContainsNoCredentialInputsAndCompletesTwoShopGroup() {
    CashierRobot robot = launchCashier(orderGroupWithTwoShops());
    assertThat(robot.inputLabels()).doesNotContain("卡号", "密码", "验证码");
    robot.choose("支付宝").succeed().await();
    assertThat(loadOrders()).allMatch(order -> order.status() == PAID);
}
```

- [ ] **Step 2: Run handler/UI/E2E tests**

Run: `mvn -pl vcampus-server,vcampus-client -am -Dtest=ShopHandlersTest,ShopUiTest,ShopEndToEndTest test`

Expected: FAIL before handlers/pages exist.

- [ ] **Step 3: Register exact commands and implement asynchronous pages**

```java
router.register("SHOP_CHECKOUT", buyerHandler(CheckoutCommand.class,
        shopService::checkout));
router.register("SHOP_SIMULATE_PAYMENT", buyerHandler(
        SimulatePaymentCommand.class, shopService::simulatePayment));
router.register("SHOP_REVIEW_SELLER_APPLICATION", adminHandler(
        ReviewSellerApplicationCommand.class, adminService::reviewApplication));
```

Disable submit buttons while futures are pending, preserve cart/form state on errors, and show `SHOP_PRICE_CHANGED` with an explicit latest-price confirmation action.

- [ ] **Step 4: Run full shop verification**

Run: `mvn -pl vcampus-common,vcampus-server,vcampus-client -am verify`

Expected: PASS for seller approval, recommendations/fallback, persisted cart, cross-shop orders, five-unit contention, payment retry/idempotency/expiry, ownership, UI states, and privacy scan.

- [ ] **Step 5: Commit the completed module**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-client/src/main/java/edu/seu/vcampus/client/shop vcampus-server/src/test vcampus-client/src/test
git commit -m "feat(shop): complete multi-merchant marketplace"
```
