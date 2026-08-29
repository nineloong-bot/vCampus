# Virtual Campus Multi-Merchant Shop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a campus multi-merchant marketplace with seller application lifecycle, shop suspension/recovery, products/SKUs, storefront browsing, price filtering and sorting, persisted carts, cross-shop checkout, inventory reservation, retryable simulated payments, order fulfillment, administration, and seventeen UI-spec-compliant Swing pages.

**Architecture:** Buyer, seller, and admin services share repositories but expose separate interfaces and permissions. Checkout locks sorted SKU keys and creates one order group with per-shop orders; payment attempts are append-only while the aggregate payment owns the final state and inventory transition.

**Tech Stack:** JDK 21, Maven, Swing, UCanAccess, BigDecimal, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-08-24-vcampus-shop-module-design.md`, `docs/superpowers/specs/2026-08-24-vcampus-overall-architecture-design.md`, and `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md`

## Global Constraints

- Complete foundation and user plans first.
- Seller capability is derived from an approved active shop; never mutate the user's base role.
- Never collect, persist, or log real payment accounts, card numbers, passwords, or verification codes.
- Money uses `BigDecimal`; never calculate price or amount with `double`.
- Catalogs display, filter, and sort by the minimum price among enabled SKUs with available stock; price bounds are inclusive.
- The default catalog order is `SALES_DESC`; equal primary values are ordered by product creation time descending.
- Lock all affected SKU IDs in sorted order for checkout/payment.
- A failed payment attempt leaves the aggregate payment `PENDING`; only success, explicit cancellation, or expiry releases/consumes the reservation.
- Payment success increments each product's `salesCount` by purchased quantity in the same transaction that finalizes payment.
- Buyer shop navigation uses the `MainFrame` navigator and `CardLayout`; route history contains at most 20 entries and ignores the current route.
- Do not implement refunds or real payment-network calls.
- Seller applications follow `DRAFT → PENDING → APPROVED` or `PENDING → REJECTED → DRAFT`; shop suspension/recovery changes only `ACTIVE ↔ SUSPENDED` and never rewrites the approved application.
- Complete the shared UI design-system plan before Task 7; shop pages may use the showcase template only where the UI specification permits it.

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
- Produces: `saveDraft`, `submitApplication`, `searchApplications`, `reviewApplication`, `suspendShop`, `resumeShop`, and `requireOwnedActiveShop`.

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

@Test
void rejectedApplicationReturnsToDraftWithoutChangingApprovedShopState() {
    SellerApplication rejected = seedRejectedApplication("user-1");
    SellerApplication draft = service.saveDraft(edit(rejected, rejected.rowVersion()));
    assertThat(draft.status()).isEqualTo(DRAFT);
    assertThat(service.submitApplication(draft.applicationId(), draft.rowVersion()).status())
            .isEqualTo(PENDING);
    Shop approvedShop = seedApprovedActiveShop("user-2");
    admin.suspendShop(approvedShop.shopId(), "违规商品", approvedShop.rowVersion());
    assertThat(applications.requireApprovedForOwner("user-2").status()).isEqualTo(APPROVED);
    assertThat(shops.require(approvedShop.shopId()).status()).isEqualTo(SUSPENDED);
}
```

- [ ] **Step 2: Run seller tests**

Run: `mvn -pl vcampus-server -am -Dtest=SellerApplicationServiceTest test`

Expected: FAIL because schema/service are absent.

- [ ] **Step 3: Implement all twelve shop tables and seller workflow**

```java
return locks.withLocks(List.of(key("SELLER_APPLICATION", applicationId),
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

Expected: PASS for teacher/student eligibility, inactive account rejection, exact draft/pending/rejected resubmission transitions, one active shop, atomic approval, ownership, and independent shop suspension/recovery.

- [ ] **Step 5: Commit seller foundation**

```bash
git add vcampus-database/schema/050_shop.sql vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test
git commit -m "feat(shop): add seller approval and shop persistence"
```

### Task 2: Products, SKUs, Storefront Browsing, Filtering, and Sorting

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductDetail.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductSummary.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopSummary.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopDetail.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/HomeProductQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductSearchQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopProductQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductSortMode.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ProductService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ShopService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ProductServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/BuyerShopServiceTest.java`

**Interfaces:**
- Consumes: owned active shop, product, and SKU repositories.
- Produces: product/SKU CRUD, `getHomeProducts`, `searchProducts`, `getProduct`, `getShop`, and `getShopProducts`.

- [ ] **Step 1: Write minimum-price, inclusive-filter, sorting, and shop-isolation tests**

```java
@Test
void filtersByMinimumSellableSkuPriceAndSortsByPriceDescending() {
    seedProduct("p-low", "shop-1", 3, created("2026-08-24T10:00:00Z"),
            disabledSku("1.00"), sellableSku("8.00", 1));
    seedProduct("p-high", "shop-1", 1, created("2026-08-24T11:00:00Z"),
            sellableSku("12.00", 2), sellableSku("20.00", 2));
    seedProduct("p-empty", "shop-1", 99, created("2026-08-24T12:00:00Z"),
            sellableSku("10.00", 0));

    ProductSearchQuery query = new ProductSearchQuery(null, null,
            money("8.00"), money("12.00"), PRICE_DESC, 0, 20);

    assertThat(service.searchProducts(query).items())
            .extracting(ProductSummary::productId, ProductSummary::minimumPrice)
            .containsExactly(tuple("p-high", money("12.00")),
                    tuple("p-low", money("8.00")));
}

@Test
void shopPageRejectsSuspendedShopAndNeverLeaksOtherShopsProducts() {
    seedActiveShop("shop-1");
    seedActiveProduct("p-1", "shop-1", sellableSku("9.00", 2));
    seedActiveProduct("p-2", "shop-2", sellableSku("11.00", 2));

    assertThatThrownBy(() -> service.getShop("missing-shop"))
            .isInstanceOf(ShopNotFoundException.class)
            .hasMessageContaining("SHOP_NOT_FOUND");

    ShopProductQuery query = new ShopProductQuery("shop-1", null, null,
            null, null, SALES_DESC, 0, 20);
    assertThat(service.getShopProducts(query).items())
            .extracting(ProductSummary::productId).containsExactly("p-1");

    suspendShop("shop-1");
    assertThatThrownBy(() -> service.getShopProducts(query))
            .isInstanceOf(ShopSuspendedException.class);
}
```

- [ ] **Step 2: Run product and storefront tests**

Run: `mvn -pl vcampus-server -am -Dtest=ProductServiceTest,BuyerShopServiceTest test`

Expected: FAIL before services exist.

- [ ] **Step 3: Implement query DTOs, validation, catalog projection, and storefront lookup**

```java
public enum ProductSortMode { SALES_DESC, PRICE_DESC }

public record HomeProductQuery(BigDecimal minPrice, BigDecimal maxPrice,
        ProductSortMode sortMode, int pageNumber, int pageSize)
        implements Serializable {}

public record ProductSearchQuery(String keyword, String category,
        BigDecimal minPrice, BigDecimal maxPrice, ProductSortMode sortMode,
        int pageNumber, int pageSize) implements Serializable {}

public record ShopProductQuery(String shopId, String keyword, String category,
        BigDecimal minPrice, BigDecimal maxPrice, ProductSortMode sortMode,
        int pageNumber, int pageSize) implements Serializable {}

public record ShopSummary(String shopId, String shopName)
        implements Serializable {}

public record ShopDetail(String shopId, String shopName, String description,
        String category, String contact, ShopStatus shopStatus)
        implements Serializable {}

private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
    if ((minPrice != null && minPrice.signum() < 0)
            || (maxPrice != null && maxPrice.signum() < 0)
            || (minPrice != null && maxPrice != null
                    && minPrice.compareTo(maxPrice) > 0)) {
        throw new ShopException("SHOP_PRICE_FILTER_INVALID");
    }
}

private ProductSortMode effectiveSort(ProductSortMode requested) {
    return requested == null ? ProductSortMode.SALES_DESC : requested;
}
```

The repository query joins active shops and products to enabled SKUs where `stockQuantity - reservedQuantity > 0`, groups by product, and projects `MIN(price)` as `minimumPrice`. Apply `minimumPrice >= minPrice` and `minimumPrice <= maxPrice` only for supplied bounds. Order by `salesCount DESC, createdAt DESC` for `SALES_DESC`, or `minimumPrice DESC, createdAt DESC` for `PRICE_DESC`. `getShopProducts` adds an immutable `shopId` predicate after `getShop` verifies that the shop exists and is `ACTIVE`. `ProductDetail` contains one `ShopSummary`; `ShopDetail` and `ProductSummary` contain no nested detail objects or product collections.

- [ ] **Step 4: Run product tests**

Run: `mvn -pl vcampus-server -am -Dtest=ProductServiceTest,BuyerShopServiceTest,ShopOwnershipTest test`

Expected: PASS for owner-only mutation, invalid price ranges, inclusive bounds, minimum sellable SKU projection, default sales order, price order, creation-time tie-breaking, inactive inventory filtering, `SHOP_NOT_FOUND`, `SHOP_SUSPENDED`, active shop lookup, and shop isolation.

- [ ] **Step 5: Commit catalog and storefront behavior**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test
git commit -m "feat(shop): add catalog filtering and storefront browsing"
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
List<ResourceKey> checkoutKeys = new ArrayList<>(skuKeys);
checkoutKeys.add(key("CART", buyerId));
return locks.withLocks(checkoutKeys, () -> transactions.inTransaction(c ->
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
- Consumes: payment/order/order-item/product/SKU/reservation repositories.
- Produces: retryable attempts, aggregate terminal payment, inventory consume/release, idempotent product sales totals, restart recovery.

- [ ] **Step 1: Write failure-retry, duplicate-success, and expiry-race tests**

```java
@Test
void failedAttemptCanRetryAndDuplicateSuccessConsumesAndCountsSalesOnce() {
    Payment payment = seedPendingPaymentWithReservation("product-1", "sku-1", 2);
    service.simulate(token, command(payment, ALIPAY, FAILED));
    assertThat(payments.require(payment.id()).status()).isEqualTo(PENDING);
    service.simulate(token, command(payment, WECHAT, SUCCEEDED));
    service.simulate(token, command(payment, WECHAT, SUCCEEDED));
    assertThat(skus.require("sku-1")).extracting(Sku::stockQuantity,
            Sku::reservedQuantity).containsExactly(8L, 0L);
    assertThat(products.require("product-1").salesCount()).isEqualTo(2L);
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
                case SUCCEEDED -> completeConsumeAndCountSales(
                        c, payment, command.channel());
                case CANCELLED -> cancelAndRelease(c, payment);
                case STARTED -> throw new ValidationException("STARTED is not a result");
            };
        }));
```

`completeConsumeAndCountSales` loads the order items for the payment, aggregates quantities by `productId`, consumes each SKU reservation, increments each product's `salesCount`, and changes the payment and orders to their success states in the same transaction. The early `payment.status() != PENDING` return makes a repeated success callback change neither inventory nor sales totals. The expiry job uses the same payment/SKU locks. On startup it processes every `PENDING` payment whose reservation expired; rerunning changes zero additional rows.

- [ ] **Step 4: Run payment and recovery verification**

Run: `mvn -pl vcampus-server -am -Dtest=SimulatedPaymentServiceTest,PaymentExpiryRaceTest,ReservationRecoveryTest test`

Expected: PASS for retry, idempotent inventory consumption, idempotent sales accumulation, explicit cancel, expiry, race uniqueness, and inventory conservation.

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

### Task 7: Message Handlers, Navigation, Seventeen UI-Spec-Compliant Swing Pages, and Acceptance

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/BuyerShopHandlers.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/SellerShopHandlers.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/AdminShopHandlers.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientService.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopRoute.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigator.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ShopHomePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductSearchPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductDetailPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/BuyerShopPanel.java`
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
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ShopNavigationTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/ShopEndToEndTest.java`
- Modify: `docs/ui-review/manifest.md`

**Interfaces:**
- Consumes: all shop services, router, authorization, async client, and `MainFrame`'s `CardLayout` host.
- Produces: complete buyer/seller/admin message surface, seventeen pages composed from the shared UI design system, and bounded route-based buyer navigation.

- [ ] **Step 1: Write navigation, cashier privacy, and cross-shop E2E tests**

```java
@Test
void navigationUsesIdsIgnoresCurrentRouteAndKeepsTwentyHistoryEntries() {
    ShopNavigator navigator = navigatorWithRecordingFactory();
    navigator.open(new ProductDetailRoute("product-1"));
    navigator.open(new BuyerShopRoute("shop-1"));
    navigator.open(new ProductDetailRoute("product-2"));
    navigator.open(new ProductDetailRoute("product-2"));
    IntStream.range(3, 24).forEach(i ->
            navigator.open(new ProductDetailRoute("product-" + i)));

    assertThat(navigator.current()).isEqualTo(new ProductDetailRoute("product-23"));
    assertThat(navigator.history()).hasSize(20);
    assertThat(navigator.history()).allMatch(route ->
            route instanceof ProductDetailRoute || route instanceof BuyerShopRoute);
}

@Test
void cashierContainsNoCredentialInputsAndCompletesTwoShopGroup() {
    CashierRobot robot = launchCashier(orderGroupWithTwoShops());
    assertThat(robot.inputLabels()).doesNotContain("卡号", "密码", "验证码");
    robot.choose("支付宝").succeed().await();
    assertThat(loadOrders()).allMatch(order -> order.status() == PAID);
}

@Test
void shopPagesUseOnlyPermittedTemplatesAndPassAccessibilityAudit() {
    UiAuditResult audit = UiComplianceAudit.inspect(shopPages());
    assertThat(audit.pagesWithoutTemplate()).isEmpty();
    assertThat(audit.disallowedImageGrids()).isEmpty();
    assertThat(audit.regionsWithMultiplePrimaryButtons()).isEmpty();
    assertThat(audit.inaccessibleControls()).isEmpty();
    assertThat(audit.staleOrDisposedAsyncUpdates()).isEmpty();
}
```

- [ ] **Step 2: Run handler/UI/E2E tests**

Run: `mvn -pl vcampus-server,vcampus-client -am -Dtest=ShopHandlersTest,ShopUiTest,ShopNavigationTest,ShopEndToEndTest test`

Expected: FAIL before handlers/pages exist.

- [ ] **Step 3: Register exact commands and implement route-based asynchronous pages**

```java
public sealed interface ShopRoute permits ProductDetailRoute, BuyerShopRoute {}
public record ProductDetailRoute(String productId) implements ShopRoute {}
public record BuyerShopRoute(String shopId) implements ShopRoute {}

public void open(ShopRoute target) {
    if (target.equals(current)) return;
    if (current != null) {
        history.addLast(current);
        if (history.size() > 20) history.removeFirst();
    }
    current = target;
    mainFrame.showCard(routeKey(target), () -> pageFactory.create(target));
}

router.register("SHOP_GET_SHOP", buyerHandler(String.class,
        shopService::getShop));
router.register("SHOP_GET_SHOP_PRODUCTS", buyerHandler(
        ShopProductQuery.class, shopService::getShopProducts));
```

Register buyer commands `SHOP_HOME`, `SHOP_SEARCH_PRODUCTS`, `SHOP_GET_PRODUCT`, `SHOP_GET_SHOP`, `SHOP_GET_SHOP_PRODUCTS`, `SHOP_GET_CART`, `SHOP_CART_ADD`, `SHOP_CART_UPDATE`, `SHOP_CART_REMOVE`, `SHOP_CHECKOUT`, `SHOP_SIMULATE_PAYMENT`, `SHOP_GET_MY_ORDERS`, `SHOP_CANCEL_ORDER_GROUP`, and `SHOP_CONFIRM_RECEIPT`. Register seller commands `SHOP_APPLY_SELLER`, `SHOP_GET_SELLER_APPLICATION`, `SHOP_UPDATE_PROFILE`, `SHOP_CREATE_PRODUCT`, `SHOP_UPDATE_PRODUCT`, `SHOP_CHANGE_PRODUCT_STATUS`, `SHOP_GET_SELLER_ORDERS`, and `SHOP_UPDATE_ORDER_STATUS`. Register admin commands `SHOP_SEARCH_SELLER_APPLICATIONS`, `SHOP_REVIEW_SELLER_APPLICATION`, `SHOP_SUSPEND`, `SHOP_SEARCH_PLATFORM_ORDERS`, and `SHOP_SEARCH_PAYMENTS`; every write handler enforces its idempotency key.

`ShopHomePanel`, `ProductSearchPanel`, and `BuyerShopPanel` expose inclusive minimum/maximum price controls and `SALES_DESC`/`PRICE_DESC` selection, send their corresponding query DTO, and render `minimumPrice` as `¥xx 起`. `ProductDetailPanel` renders `ShopSummary` and submits `BuyerShopRoute(shopId)` through the navigator. `BuyerShopPanel` renders `ShopDetail`, pages only that shop's products, and submits `ProductDetailRoute(productId)` for item clicks. Pages never instantiate or retain another page. Disable submit buttons while futures are pending, preserve cart/form state on errors, show `SHOP_PRICE_FILTER_INVALID` beside the price controls, and show `SHOP_PRICE_CHANGED` with an explicit latest-price confirmation action.

Map order/review/governance pages to query-list or detail templates, seller application and checkout to edit-form templates, dashboard/product administration to the management template, and only home/search/product detail/cart/storefront browsing to the showcase template. `SellerDashboardPanel` uses one horizontal summary strip, `CartPanel` uses a line-item list plus one checkout summary, and only the permitted product pages use adaptive 3–4-column image grids. Use all required states, visible focus, shared dialogs/notifications, and latest-request/disposal guards.

- [ ] **Step 4: Run full shop verification**

Run: `mvn -pl vcampus-common,vcampus-server,vcampus-client -am verify`

Expected: PASS for seller lifecycle and shop suspension/recovery, storefront browsing, inclusive price filtering, sales/price sorting, route history bounds, persisted cart, cross-shop orders, five-unit contention, payment and sales-count idempotency, expiry recovery, ownership, UI design-system compliance at required sizes/scaling, screenshot manifest entries, all UI states, and privacy scan.

- [ ] **Step 5: Commit the completed module**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-client/src/main/java/edu/seu/vcampus/client/shop vcampus-server/src/test vcampus-client/src/test docs/ui-review/manifest.md
git commit -m "feat(shop): complete multi-merchant marketplace"
```
