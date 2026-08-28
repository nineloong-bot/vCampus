# Virtual Campus Multi-Merchant Shop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a campus multi-merchant marketplace with seller application lifecycle, shop suspension/recovery, products/SKUs, storefront browsing, price filtering and sorting, persisted carts, cross-shop checkout, inventory reservation, retryable simulated payments, order fulfillment, administration, and seventeen UI-spec-compliant Swing pages.

**Architecture:** Buyer, seller, and admin services share repositories but expose separate interfaces and permissions. Checkout locks sorted SKU keys and creates one order group with per-shop orders; payment attempts are append-only while the aggregate payment owns the final state and inventory transition.

**Tech Stack:** JDK 21, Maven, Swing, UCanAccess, BigDecimal, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-08-24-vcampus-shop-module-design.md`, `docs/superpowers/specs/2026-08-24-vcampus-overall-architecture-design.md`, and `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md`

## Global Constraints

- The shop design spec is the source of truth. This plan may add implementation detail but may not add, remove, rename, or weaken a requirement, DTO field, service method, message command, table field, state transition, error code, page, or acceptance criterion from that spec.
- Complete foundation and user plans first.
- Seller capability is derived from an approved active shop; never mutate the user's base role.
- Never collect, persist, or log real payment accounts, card numbers, passwords, or verification codes.
- Money uses `BigDecimal`; never calculate price or amount with `double`.
- Reuse the foundation module's `PageResult<T>`; do not create a shop-local pagination type. Shop DTO timestamps use `Instant`, and every record crossing Socket boundaries implements `Serializable`.
- Catalogs display, filter, and sort by the minimum price among enabled SKUs with available stock; price bounds are inclusive.
- The default catalog order is `SALES_DESC`; equal primary values are ordered by product creation time descending.
- Lock all affected SKU IDs in sorted order for checkout/payment.
- A failed payment attempt leaves the aggregate payment `PENDING`; only success, explicit cancellation, or expiry releases/consumes the reservation.
- Payment success increments each product's `salesCount` by purchased quantity in the same transaction that finalizes payment.
- Buyer shop navigation uses the `MainFrame` navigator and `CardLayout`; route history contains at most 20 entries and ignores the current route.
- Do not implement refunds or real payment-network calls.
- Seller applications follow `DRAFT → PENDING → APPROVED` or `PENDING → REJECTED → DRAFT`; shop suspension/recovery changes only `ACTIVE ↔ SUSPENDED` and never rewrites the approved application.
- Complete the shared UI design-system plan before Task 7; shop pages may use the showcase template only where the UI specification permits it.

## Spec Traceability

| Shop spec sections | Implemented by | Required verification |
|---|---|---|
| 1–3: scope, identities, seller application, shop suspension/recovery | Task 1 | `SellerApplicationServiceTest`, `ShopOwnershipTest`, `ShopStatusServiceTest` |
| 4–5: product/SKU rules, minimum sellable price, filtering/sorting, buyer storefront | Task 2 | `ProductServiceTest`, `BuyerShopServiceTest` |
| 6: persisted cart | Task 3 | `CartServiceTest`, `ConcurrentCartUpdateTest` |
| 6–7: cross-shop checkout, snapshots, reservation | Task 4 | `CheckoutServiceTest`, `ConcurrentCheckoutTest`, `OrderAmountInvariantTest` |
| 7–8: retryable payment, expiry, inventory/sales idempotency | Task 5 | `SimulatedPaymentServiceTest`, `PaymentExpiryRaceTest`, `ReservationRecoveryTest` |
| 8: buyer orders, seller fulfillment, group completion, platform governance | Task 6 | `OrderLifecycleTest`, `ShopAdminAuthorizationTest` |
| 9–12: 17 Swing pages, DTO wiring, services, exact Socket commands | Tasks 1–7, integrated by Task 7 | `ShopHandlersTest`, `ShopUiTest`, `ShopNavigationTest` |
| 13: all 12 Access tables and indexes | Task 1 schema; Tasks 2–6 repositories | schema integration tests plus full `verify` |
| 14–16: locks, transactions, exact errors, audit/privacy | Tasks 1–7 | concurrency tests, error-contract test, privacy scan |
| 17–19: acceptance, file boundaries, complete demonstration | Task 7 | `ShopEndToEndTest` and module-wide `verify` |

**Schema contract:** `vcampus-database/schema/050_shop.sql` creates exactly the twelve tables named by spec section 13: `tblSellerApplication`, `tblShop`, `tblProduct`, `tblProductSku`, `tblCart`, `tblCartItem`, `tblOrderGroup`, `tblOrder`, `tblOrderItem`, `tblPayment`, `tblPaymentAttempt`, and `tblInventoryReservation`. Column names, Access types, nullability, uniqueness, foreign keys, defaults, status values, audit fields, and indexes must match the spec verbatim. Task 1 creates the complete schema once; Tasks 2–6 implement the repositories and behavior that consume their assigned tables without renaming or adding shop-local columns.

**Error ownership:** Task 1 owns `SHOP_SELLER_APPLICATION_EXISTS`, `SHOP_SELLER_APPLICATION_STATUS_INVALID`, `SHOP_SELLER_NOT_APPROVED`, `SHOP_NOT_FOUND`, `SHOP_NOT_OWNER`, `SHOP_SUSPENDED`, and `SHOP_STATUS_INVALID`. Task 2 owns `SHOP_PRODUCT_INACTIVE`, `SHOP_SKU_UNAVAILABLE`, and `SHOP_PRICE_FILTER_INVALID`. Task 4 owns `SHOP_PRICE_CHANGED`, `SHOP_INSUFFICIENT_STOCK`, and `SHOP_CART_EMPTY`. Task 5 owns `PAYMENT_ALREADY_COMPLETED`, `PAYMENT_NOT_PENDING`, and `PAYMENT_AMOUNT_MISMATCH`. Task 6 owns `SHOP_ORDER_STATUS_INVALID` and `SHOP_ORDER_NOT_OWNED`. Task 7 verifies that the public protocol exposes exactly this set and no renamed equivalents.

---

### Task 1: Shop Schema, Seller Application, and Approval

**Spec coverage:** Sections 2–3, 10–16 seller/shop-status contracts, tables 13.1–13.2, and the corresponding section 17 acceptance cases.

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopErrorCode.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerApplicationStatus.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopStatus.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerReviewDecision.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SaveSellerDraftCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SubmitSellerApplicationCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ReviewSellerApplicationCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SuspendShopCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ResumeShopCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerApplicationQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerApplicationView.java`
- Create: `vcampus-database/schema/050_shop.sql`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/ShopRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/AccessShopRepository.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/SellerApplicationService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/SellerService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ShopAdminService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/SellerApplicationServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ShopStatusServiceTest.java`

**Interfaces:**
- Consumes: `UserQueryPort`, authorization, transactions, locks.
- Produces: `saveDraft(String, SaveSellerDraftCommand)`, `submitApplication(String, SubmitSellerApplicationCommand)`, `getMyApplication(String)`, `searchApplications(SellerApplicationQuery)`, `reviewApplication(ReviewSellerApplicationCommand)`, `suspendShop(SuspendShopCommand)`, `resumeShop(ResumeShopCommand)`, `searchPlatformOrders(PlatformOrderQuery)`, `searchPayments(PaymentSearchQuery)`, and `requireOwnedActiveShop`.

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

@Test
void suspensionRequiresAuditAndResumeKeepsApplicationApproved() {
    Shop shop = seedApprovedActiveShop("user-2");
    admin.suspendShop(new SuspendShopCommand(shop.shopId(), "违规商品",
            shop.rowVersion()));
    Shop suspended = shops.require(shop.shopId());
    assertThat(suspended.status()).isEqualTo(SUSPENDED);
    assertThat(suspended.suspensionReason()).isEqualTo("违规商品");
    assertThat(suspended.suspendedByUserId()).isEqualTo(adminUserId);
    admin.resumeShop(new ResumeShopCommand(shop.shopId(), suspended.rowVersion()));
    assertThat(shops.require(shop.shopId()).status()).isEqualTo(ACTIVE);
    assertThat(applications.requireApprovedForOwner("user-2").status())
            .isEqualTo(APPROVED);
}
```

- [ ] **Step 2: Run seller tests**

Run: `mvn -pl vcampus-server -am -Dtest=SellerApplicationServiceTest test`

Expected: FAIL because schema/service are absent.

- [ ] **Step 3: Implement all twelve shop tables, exact error codes, and seller workflow**

```java
return locks.withLocks(List.of(key("SELLER_APPLICATION", applicationId),
        key("USER", applicantId)), () -> transactions.inTransaction(c -> {
    SellerApplication pending = applications.requirePending(c, applicationId, version);
    shops.requireNoActiveShop(c, pending.applicantUserId());
    Shop shop = shops.insert(c, Shop.approvedFrom(pending));
    SellerApplication approved = applications.markApproved(
            c, pending, reviewerId, clock.instant());
    return mapper.toView(approved, shop);
}));
```

`tblShop` includes `suspensionReason`, `suspendedByUserId`, and `suspendedAt` exactly as specified. A suspend operation requires a non-blank reason and records all three fields; resume changes only `SUSPENDED → ACTIVE`, retains the last suspension audit fields, and increments `rowVersion`. Draft save, submit, review, suspend, and resume reject stale versions. Implement every error symbol from spec section 15 in `ShopErrorCode`; later tasks reuse these symbols and must not introduce shop-local aliases.

```java
public enum SellerApplicationStatus { DRAFT, PENDING, APPROVED, REJECTED }
public enum ShopStatus { ACTIVE, SUSPENDED }
public record SaveSellerDraftCommand(String applicationId, String shopName,
        String description, String category, String contact,
        long expectedVersion) implements Serializable {}
public record SubmitSellerApplicationCommand(String applicationId,
        long expectedVersion) implements Serializable {}
public enum SellerReviewDecision { APPROVE, REJECT }
public record ReviewSellerApplicationCommand(String applicationId,
        SellerReviewDecision decision, String reason,
        long expectedVersion) implements Serializable {}
public record SuspendShopCommand(String shopId, String reason,
        long expectedVersion) implements Serializable {}
public record ResumeShopCommand(String shopId, long expectedVersion)
        implements Serializable {}
```

For a first draft save, require `applicationId == null` and `expectedVersion == 0`; return the generated ID/version. For later saves, require ownership, `DRAFT` or `REJECTED`, and the expected version; editing `REJECTED` changes it to `DRAFT`. Submit accepts only the owner's `DRAFT`. Reject requires a non-blank reason; approve and suspend/resume follow the state and audit rules above.

- [ ] **Step 4: Run seller integration/concurrency tests**

Run: `mvn -pl vcampus-server -am -Dtest=SellerApplicationServiceTest,ShopOwnershipTest,ShopStatusServiceTest test`

Expected: PASS for teacher/student eligibility, inactive account rejection, exact draft/pending/rejected resubmission transitions, pending-edit rejection, one active shop, atomic approval, ownership, suspension audit fields, recovery, stale-version rejection, and an unchanged approved application.

- [ ] **Step 5: Commit seller foundation**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-database/schema/050_shop.sql vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test
git commit -m "feat(shop): add seller approval and shop persistence"
```

### Task 2: Products, SKUs, Storefront Browsing, Filtering, and Sorting

**Spec coverage:** Sections 4–5, buyer pages M-01–M-04, product/shop DTOs and services in sections 10–12, tables 13.2–13.4, catalog query rules in section 14, and related section 17 acceptance cases.

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductDetail.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductSummary.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductSkuView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopSummary.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopDetail.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/HomeProductQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductSearchQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopProductQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductSortMode.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductStatus.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/CreateSkuCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/UpsertSkuCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/CreateProductCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/UpdateProductCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ChangeProductStatusCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/UpdateShopCommand.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ProductService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ShopService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ProductServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/BuyerShopServiceTest.java`

**Interfaces:**
- Consumes: owned active shop, product, and SKU repositories.
- Produces: `updateShop`, `createProduct`, `updateProduct`, `changeProductStatus`, `getHomeProducts`, `searchProducts`, `getProduct`, `getShop`, and `getShopProducts` with the exact DTO signatures from spec section 11.

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
public enum ProductStatus { DRAFT, ACTIVE, INACTIVE }
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

The repository query joins active shops and products to enabled SKUs where `stockQuantity - reservedQuantity > 0`, groups by product, and projects `MIN(unitPrice)` as `minimumPrice`. Apply `minimumPrice >= minPrice` and `minimumPrice <= maxPrice` only for supplied bounds. Order by `salesCount DESC, createdAt DESC` for `SALES_DESC`, or `minimumPrice DESC, createdAt DESC` for `PRICE_DESC`. `getShopProducts` adds an immutable `shopId` predicate after `getShop` verifies that the shop exists and is `ACTIVE`. `ProductDetail` contains one `ShopSummary`; `ShopDetail` and `ProductSummary` contain no nested detail objects or product collections.

- [ ] **Step 4: Run product tests**

Run: `mvn -pl vcampus-server -am -Dtest=ProductServiceTest,BuyerShopServiceTest,ShopOwnershipTest test`

Expected: PASS for owner-only mutation, invalid price ranges, inclusive bounds, minimum sellable SKU projection, default sales order, price order, creation-time tie-breaking, inactive inventory filtering, `SHOP_NOT_FOUND`, `SHOP_SUSPENDED`, active shop lookup, and shop isolation.

- [ ] **Step 5: Commit catalog and storefront behavior**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test
git commit -m "feat(shop): add catalog filtering and storefront browsing"
```

### Task 3: Persisted Shopping Cart

**Spec coverage:** Section 6 cart rules, buyer page M-05, cart DTOs/messages, tables 13.5–13.6, cart locking in section 14, and related section 17 acceptance cases.

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/AddCartItemCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/UpdateCartItemCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/CartView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/CartItemView.java`
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

**Spec coverage:** Sections 6–7 checkout/reservation rules, buyer page M-06, checkout DTOs/messages, tables 13.7–13.9 and 13.12, checkout locking in section 14, and related section 17 acceptance cases.

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/CheckoutCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/CheckoutItem.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/CheckoutResult.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/OrderItemView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/OrderSummary.java`
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
List<CartItem> selected = transactions.readOnly(c -> carts.requireOwnedItems(
        c, buyerId,
        command.items().stream().map(CheckoutItem::cartItemId).toList()));
List<ResourceKey> skuKeys = selected.stream().map(CartItem::skuId)
        .distinct().sorted().map(id -> key("SKU", id)).toList();
List<ResourceKey> checkoutKeys = new ArrayList<>(skuKeys);
checkoutKeys.add(key("CART", buyerId));
return locks.withLocks(checkoutKeys, () -> transactions.inTransaction(c ->
        createOrderGroupOrdersPaymentAndReservations(c, buyerId, command)));
```

The client supplies only `cartItemId` and `displayedUnitPrice`; it never supplies a trusted `skuId`. Resolve every selected cart item under buyer ownership before deriving sorted SKU lock keys, then re-read the cart and SKUs inside the transaction after locks are held. If displayed price differs and `acceptLatestPrice=false`, throw `SHOP_PRICE_CHANGED` with latest prices and create no rows. Calculate every line and total with `BigDecimal` and compare totals before commit.

- [ ] **Step 4: Run checkout and conservation tests**

Run: `mvn -pl vcampus-server -am -Dtest=CheckoutServiceTest,ConcurrentCheckoutTest,OrderAmountInvariantTest test`

Expected: PASS for cross-shop split, snapshot fields, no partial orders, five-unit race, and amount equality.

- [ ] **Step 5: Commit checkout**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test
git commit -m "feat(shop): add cross-shop checkout and reservations"
```

### Task 5: Retryable Simulated Payment and Expiry Recovery

**Spec coverage:** Sections 7–8 payment rules/states, buyer page M-07, payment DTOs/messages, tables 13.10–13.12, payment locking/recovery in section 14, privacy rules, and related section 17 acceptance cases.

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
- Produces: `simulatePayment(String, SimulatePaymentCommand)`, retryable attempts, aggregate terminal payment, inventory consume/release, idempotent product sales totals, and restart recovery.

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

@Test
void terminalAndAmountErrorsFollowThePublicContract() {
    Payment succeeded = seedSucceededPayment();
    assertThatThrownBy(() -> service.simulate(token,
            commandWithNewIdempotencyKey(succeeded, ALIPAY, SUCCEEDED)))
            .hasMessageContaining("PAYMENT_ALREADY_COMPLETED");
    Payment expired = seedExpiredPayment();
    assertThatThrownBy(() -> service.simulate(token,
            commandWithNewIdempotencyKey(expired, WECHAT, SUCCEEDED)))
            .hasMessageContaining("PAYMENT_NOT_PENDING");
    Payment mismatch = seedPendingPaymentWithMismatchedGroupAmount();
    assertThatThrownBy(() -> service.simulate(token,
            command(mismatch, BANK_CARD, SUCCEEDED)))
            .hasMessageContaining("PAYMENT_AMOUNT_MISMATCH");
    assertInventoryAndReservationsUnchanged(mismatch);
}
```

- [ ] **Step 2: Run payment tests**

Run: `mvn -pl vcampus-server -am -Dtest=SimulatedPaymentServiceTest,PaymentExpiryRaceTest test`

Expected: FAIL before payment state machine exists.

- [ ] **Step 3: Implement aggregate payment lock and append-only attempts**

```java
return locks.withLocks(paymentOrderGroupAndSortedSkuKeys(paymentId), () ->
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

`paymentOrderGroupAndSortedSkuKeys` returns `PAYMENT:<paymentId>`, `ORDER_GROUP:<orderGroupId>`, and every reservation SKU key sorted by `skuId`; after acquiring them, the transaction re-reads all rows. `completeConsumeAndCountSales` loads the order items for the payment, aggregates quantities by `productId`, consumes each SKU reservation, increments each product's `salesCount`, and changes the payment, order group, and orders to their success states in the same transaction. The early `payment.status() != PENDING` return makes a repeated success callback change neither inventory nor sales totals. The expiry job uses the identical payment/order-group/SKU key set. On startup it processes every `PENDING` payment whose reservation expired; rerunning changes zero additional rows.

- [ ] **Step 4: Run payment and recovery verification**

Run: `mvn -pl vcampus-server -am -Dtest=SimulatedPaymentServiceTest,PaymentExpiryRaceTest,ReservationRecoveryTest test`

Expected: PASS for same-key result replay, new-key terminal errors, payment/group amount mismatch with no inventory mutation, retry, idempotent inventory consumption, idempotent sales accumulation, explicit cancel, expiry, race uniqueness, and inventory conservation.

- [ ] **Step 5: Commit payment state machine**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-server/src/main/java/edu/seu/vcampus/server/shop/payment vcampus-server/src/test
git commit -m "feat(shop): add retryable simulated payments"
```

### Task 6: Order Fulfillment and Platform Governance

**Spec coverage:** Section 8 order/group states, buyer pages M-08–M-09, seller/admin order pages M-14/M-17, order/admin services and messages, tables 13.7–13.10, and related section 17 acceptance cases.

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/PlatformOrderQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/PaymentSearchQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/PlatformOrderView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerOrderView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/OrderStatus.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/OrderSearchQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerOrderQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/UpdateOrderStatusCommand.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/OrderService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ShopAdminServiceImpl.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/OrderLifecycleTest.java`

**Interfaces:**
- Consumes: paid orders, seller ownership, admin permission.
- Produces: `getMyOrders(String, OrderSearchQuery)`, `cancelOrder(String, String)`, `confirmReceipt(String, String)`, `getSellerOrders(String, SellerOrderQuery)`, `updateOrderStatus(String, UpdateOrderStatusCommand)`, `searchPlatformOrders(PlatformOrderQuery)`, and `searchPayments(PaymentSearchQuery)`.

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

@Test
void completingLastChildCompletesOrderGroupInSameTransaction() {
    OrderGroup group = seedShippedGroupWithTwoOrders();
    service.confirmReceipt(buyerToken, group.orders().get(0).orderId());
    assertThat(orderGroups.require(group.id()).status()).isNotEqualTo(COMPLETED);
    service.confirmReceipt(buyerToken, group.orders().get(1).orderId());
    assertThat(orderGroups.require(group.id()).status()).isEqualTo(COMPLETED);
}

@Test
void adminQueriesArePagedAndReadOnly() {
    seedPlatformOrdersAndPayments();
    assertThat(admin.searchPlatformOrders(new PlatformOrderQuery(
            null, null, "shop-1", PAID, 0, 20)).items())
            .allMatch(order -> order.shopId().equals("shop-1"));
    assertThat(admin.searchPayments(new PaymentSearchQuery(
            null, SUCCEEDED, ALIPAY, 0, 20)).items())
            .allMatch(payment -> payment.status() == SUCCEEDED);
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

Buyers alone confirm `SHIPPED → COMPLETED`; after each confirmation, lock and re-read the order group and set its `groupStatus=COMPLETED` only when every child order is `COMPLETED`. Only `PENDING_PAYMENT` groups can be cancelled. Admin order/payment searches are paged, read-only, and use exactly `PlatformOrderQuery` and `PaymentSearchQuery`; suspension hides catalog entries but preserves orders.

- [ ] **Step 4: Run lifecycle/authorization tests**

Run: `mvn -pl vcampus-server -am -Dtest=OrderLifecycleTest,ShopAdminAuthorizationTest test`

Expected: PASS for buyer/seller ownership, transitions, cancellation, last-child group completion, paged admin order/payment queries, and no mutations from searches.

- [ ] **Step 5: Commit lifecycle behavior**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service vcampus-server/src/test
git commit -m "feat(shop): add order fulfillment and governance"
```

### Task 7: Message Handlers, Navigation, Seventeen UI-Spec-Compliant Swing Pages, and Acceptance

**Spec coverage:** Sections 9–12 exact page/DTO/service/message wiring, sections 15–16 error/privacy contracts, and the complete sections 17–19 acceptance/file-boundary requirements.

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/BuyerShopHandlers.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/SellerShopHandlers.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/AdminShopHandlers.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientService.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopRoute.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopHomeRoute.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ProductSearchRoute.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ProductDetailRoute.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/BuyerShopRoute.java`
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
    navigator.open(new ShopHomeRoute(defaultHomeQuery()));
    navigator.open(new ProductSearchRoute(search("文具")));
    navigator.open(new ProductDetailRoute("product-1"));
    navigator.open(new BuyerShopRoute("shop-1"));
    navigator.open(new ProductDetailRoute("product-2"));
    navigator.open(new ProductDetailRoute("product-2"));
    IntStream.range(3, 24).forEach(i ->
            navigator.open(new ProductDetailRoute("product-" + i)));

    assertThat(navigator.current()).isEqualTo(new ProductDetailRoute("product-23"));
    assertThat(navigator.history()).hasSize(20);
    assertThat(navigator.history()).allMatch(route ->
            route instanceof ShopHomeRoute || route instanceof ProductSearchRoute
                    || route instanceof ProductDetailRoute
                    || route instanceof BuyerShopRoute);
}

@Test
void exactSpecCommandsAndErrorCodesAreExposed() {
    assertThat(router.registeredShopCommands()).containsExactlyInAnyOrder(
            SHOP_HOME, SHOP_SEARCH_PRODUCTS, SHOP_GET_PRODUCT, SHOP_GET_SHOP,
            SHOP_GET_SHOP_PRODUCTS, SHOP_GET_CART, SHOP_CART_ADD,
            SHOP_CART_UPDATE, SHOP_CART_REMOVE, SHOP_CHECKOUT,
            SHOP_SIMULATE_PAYMENT, SHOP_GET_MY_ORDERS,
            SHOP_CANCEL_ORDER_GROUP, SHOP_CONFIRM_RECEIPT,
            SHOP_SAVE_SELLER_DRAFT, SHOP_SUBMIT_SELLER_APPLICATION,
            SHOP_GET_SELLER_APPLICATION, SHOP_UPDATE_PROFILE,
            SHOP_CREATE_PRODUCT, SHOP_UPDATE_PRODUCT,
            SHOP_CHANGE_PRODUCT_STATUS, SHOP_GET_SELLER_ORDERS,
            SHOP_UPDATE_ORDER_STATUS, SHOP_SEARCH_SELLER_APPLICATIONS,
            SHOP_REVIEW_SELLER_APPLICATION, SHOP_SUSPEND, SHOP_RESUME,
            SHOP_SEARCH_PLATFORM_ORDERS, SHOP_SEARCH_PAYMENTS);
    assertThat(EnumSet.allOf(ShopErrorCode.class)).containsExactlyInAnyOrder(
            SHOP_SELLER_APPLICATION_EXISTS,
            SHOP_SELLER_APPLICATION_STATUS_INVALID,
            SHOP_SELLER_NOT_APPROVED, SHOP_NOT_FOUND, SHOP_NOT_OWNER,
            SHOP_SUSPENDED, SHOP_STATUS_INVALID, SHOP_PRODUCT_INACTIVE,
            SHOP_SKU_UNAVAILABLE, SHOP_PRICE_FILTER_INVALID,
            SHOP_PRICE_CHANGED, SHOP_INSUFFICIENT_STOCK, SHOP_CART_EMPTY,
            SHOP_ORDER_STATUS_INVALID, SHOP_ORDER_NOT_OWNED,
            PAYMENT_ALREADY_COMPLETED, PAYMENT_NOT_PENDING,
            PAYMENT_AMOUNT_MISMATCH);
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
public sealed interface ShopRoute permits ShopHomeRoute, ProductSearchRoute,
        ProductDetailRoute, BuyerShopRoute {}
public record ShopHomeRoute(HomeProductQuery query) implements ShopRoute {}
public record ProductSearchRoute(ProductSearchQuery query) implements ShopRoute {}
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

Register buyer commands `SHOP_HOME`, `SHOP_SEARCH_PRODUCTS`, `SHOP_GET_PRODUCT`, `SHOP_GET_SHOP`, `SHOP_GET_SHOP_PRODUCTS`, `SHOP_GET_CART`, `SHOP_CART_ADD`, `SHOP_CART_UPDATE`, `SHOP_CART_REMOVE`, `SHOP_CHECKOUT`, `SHOP_SIMULATE_PAYMENT`, `SHOP_GET_MY_ORDERS`, `SHOP_CANCEL_ORDER_GROUP`, and `SHOP_CONFIRM_RECEIPT`. Register seller commands `SHOP_SAVE_SELLER_DRAFT`, `SHOP_SUBMIT_SELLER_APPLICATION`, `SHOP_GET_SELLER_APPLICATION`, `SHOP_UPDATE_PROFILE`, `SHOP_CREATE_PRODUCT`, `SHOP_UPDATE_PRODUCT`, `SHOP_CHANGE_PRODUCT_STATUS`, `SHOP_GET_SELLER_ORDERS`, and `SHOP_UPDATE_ORDER_STATUS`. Register admin commands `SHOP_SEARCH_SELLER_APPLICATIONS`, `SHOP_REVIEW_SELLER_APPLICATION`, `SHOP_SUSPEND`, `SHOP_RESUME`, `SHOP_SEARCH_PLATFORM_ORDERS`, and `SHOP_SEARCH_PAYMENTS`; every write handler enforces its idempotency key and returns the first result for an identical retry.

`ShopHomePanel`, `ProductSearchPanel`, and `BuyerShopPanel` expose inclusive minimum/maximum price controls and `SALES_DESC`/`PRICE_DESC` selection, send their corresponding query DTO, and render `minimumPrice` as `¥xx 起`. Home and search pages submit `ShopHomeRoute` and `ProductSearchRoute` containing their current query state before opening a product. `ProductDetailPanel` renders `ShopSummary` and submits `BuyerShopRoute(shopId)` through the navigator. `BuyerShopPanel` renders `ShopDetail`, pages only that shop's products, and submits `ProductDetailRoute(productId)` for item clicks. Back navigation restores the prior home/search query from the route; pages never instantiate or retain another page. Disable submit buttons while futures are pending, preserve cart/form state on errors, show `SHOP_PRICE_FILTER_INVALID` beside the price controls, and show `SHOP_PRICE_CHANGED` with an explicit latest-price confirmation action.

Map order/review/governance pages to query-list or detail templates, seller application and checkout to edit-form templates, dashboard/product administration to the management template, and only home/search/product detail/cart/storefront browsing to the showcase template. `SellerDashboardPanel` uses one horizontal summary strip, `CartPanel` uses a line-item list plus one checkout summary, and only the permitted product pages use adaptive 3–4-column image grids. Use all required states, visible focus, shared dialogs/notifications, and latest-request/disposal guards.

- [ ] **Step 4: Run full shop verification**

Run: `mvn -pl vcampus-common,vcampus-server,vcampus-client -am verify`

Expected: PASS for seller lifecycle and shop suspension/recovery, storefront browsing, inclusive price filtering, sales/price sorting, route history bounds, persisted cart, cross-shop orders, five-unit contention, payment and sales-count idempotency, expiry recovery, ownership, UI design-system compliance at required sizes/scaling, screenshot manifest entries, all UI states, and privacy scan.

- [ ] **Step 5: Commit the completed module**

```bash
git add vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-client/src/main/java/edu/seu/vcampus/client/shop vcampus-server/src/test vcampus-client/src/test docs/ui-review/manifest.md
git commit -m "feat(shop): complete multi-merchant marketplace"
```
