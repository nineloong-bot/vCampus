# Shop Buyer Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Execute inline in the primary session and stop at each review checkpoint.

**Goal:** Stabilize the existing Demo connection change, correct buyer flows, and replace the buyer catalog with an image-card grid backed by product/SKU aggregation.

**Architecture:** Common carries stable catalog data including an optional HTTPS cover URL. Server persists one generic product with multiple SKU rows and enforces buyer permissions. Client separates catalog query/navigation from an injectable image loader and card renderer so the final visual layer can be replaced independently.

**Tech Stack:** Java 21, Swing, Java serialization, CompletableFuture, JDBC/UCanAccess, JUnit 5, AssertJ, Maven.

**Spec:** `docs/superpowers/specs/2026-08-31-vcampus-shop-seller-admin-design.md`

## Global Constraints

- Modify only `common/shop`, `server/shop`, `client/shop`, Shop database, Demo, tests, and Shop documentation.
- Categories are exactly `文具`, `图书`, `生活用品`, `药品`, `其他`.
- One product is a generic type; concrete color, size, package, or edition belongs to SKU.
- Cover URLs are optional HTTPS URLs of at most 2048 characters and cannot contain credentials.
- Administrators cannot perform buyer mutations; owners cannot buy their own shop products.
- Preserve `logs/`; do not push, merge, rebase, delete, roll back, or clean.

---

### Task 1: Isolate and commit the verified connection-status fix

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMain.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMainTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthEndToEndTest.java`

**Interfaces:**
- Consumes: `MainFrame(UserView, ClientConnection)` from the existing client shell.
- Produces: Demo client construction that reports the active connection without changing User Management.

- [ ] **Step 1: Inspect the pending diff and prove it is limited to connection composition**

Run:

```powershell
git diff -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMain.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMainTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthEndToEndTest.java
```

Expected: production code passes the authenticated `UserView` and active `ClientConnection` to `MainFrame`; tests assert that constructor path.

- [ ] **Step 2: Re-run the focused tests**

Run:

```powershell
mvn -pl vcampus-client -am -Dtest=ShopAuthDemoClientMainTest,ShopAuthEndToEndTest test
```

Expected: PASS with no User module source modification.

- [ ] **Step 3: Commit only the three verified files**

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMain.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMainTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthEndToEndTest.java
git commit -m "fix(shop-demo): bind shell status to active connection"
```

### Task 2: Define the generic-product catalog contract

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopCategories.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductSummary.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductDetail.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductView.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/CreateProductCommand.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/UpdateProductCommand.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopErrorCode.java`
- Create: `vcampus-common/src/test/java/edu/seu/vcampus/common/shop/CatalogContractTest.java`

**Interfaces:**
- Produces: `ShopCategories.ALL`, `ShopCategories.requireSupported(String)`, and nullable `coverImageUrl` on catalog/product DTOs.
- Produces error symbols: `SHOP_CATEGORY_INVALID`, `SHOP_PRODUCT_NAME_EXISTS`, `SHOP_COVER_IMAGE_URL_INVALID`, `SHOP_BUYER_FORBIDDEN`, `SHOP_SELF_PURCHASE_FORBIDDEN`.

- [ ] **Step 1: Write failing Common contract tests**

```java
@Test void exposesExactlyFiveCategories() {
    assertThat(ShopCategories.ALL)
            .containsExactly("文具", "图书", "生活用品", "药品", "其他");
}

@Test void productSummaryCarriesCoverUrlThroughSerialization() {
    ProductSummary value = new ProductSummary("p", "s", "店", "中性笔", "文具",
            "https://img.example/pen.png", new BigDecimal("2.80"), 9, Instant.EPOCH);
    assertThat(roundTrip(value)).isEqualTo(value);
}
```

- [ ] **Step 2: Run the Common test and verify red**

Run: `mvn -pl vcampus-common -Dtest=CatalogContractTest test`

Expected: FAIL because `ShopCategories` and cover URL components do not exist.

- [ ] **Step 3: Add the minimal immutable contract**

```java
public final class ShopCategories {
    public static final List<String> ALL = List.of("文具", "图书", "生活用品", "药品", "其他");
    public static String requireSupported(String value) {
        String normalized = Objects.requireNonNull(value, "category").strip();
        if (!ALL.contains(normalized)) throw new IllegalArgumentException("unsupported category");
        return normalized;
    }
    private ShopCategories() { }
}
```

Add `String coverImageUrl` after `category` in `ProductSummary`; add it after `description` in `ProductDetail`, `ProductView`, `CreateProductCommand`, and `UpdateProductCommand`. Preserve defensive copies of SKU lists.

- [ ] **Step 4: Run Common tests**

Run: `mvn -pl vcampus-common test`

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-common/src/test/java/edu/seu/vcampus/common/shop
git commit -m "feat(shop): define product catalog contract"
```

### Task 3: Persist cover URLs and enforce product grouping

**Files:**
- Modify: `vcampus-database/schema/050_shop.sql`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/domain/Product.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/ShopRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/AccessShopRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ProductService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopDemo.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabase.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/repository/AccessShopRepositoryTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ProductServiceTest.java`

**Interfaces:**
- Produces: `Optional<Product> findProductByNormalizedName(Connection, String shopId, String normalizedName)`.
- Produces: `ProductImageUrl.validate(String)` as a package-private service helper returning nullable normalized HTTPS URL.

- [ ] **Step 1: Write failing repository and service tests**

```java
@Test void rejectsDuplicateGenericProductNameWithinOneShop() {
    products.createProduct("owner-token", product("中性笔", "https://img.example/a.png"));
    assertThatThrownBy(() -> products.createProduct(
            "owner-token", product("  中性笔  ", "https://img.example/b.png")))
            .isInstanceOfSatisfying(ShopException.class,
                    e -> assertThat(e.code()).isEqualTo(ShopErrorCode.SHOP_PRODUCT_NAME_EXISTS));
}

@Test void rejectsNonHttpsOrCredentialedCoverUrl() {
    assertThatThrownBy(() -> products.createProduct(
            "owner-token", product("中性笔", "http://example.test/pen.png")))
            .isInstanceOfSatisfying(ShopException.class,
                    e -> assertThat(e.code()).isEqualTo(ShopErrorCode.SHOP_COVER_IMAGE_URL_INVALID));
}
```

- [ ] **Step 2: Run focused server tests and verify red**

Run: `mvn -pl vcampus-server -am -Dtest=ProductServiceTest,AccessShopRepositoryTest test`

Expected: FAIL on missing schema field and grouping checks.

- [ ] **Step 3: Add schema, mapping, and validation**

Add `coverImageUrl VARCHAR(2048)` and `normalizedProductName VARCHAR(256) NOT NULL` to `tblProduct`, plus a unique index on `(shopId, normalizedProductName)`. Populate `normalizedProductName` from `productName.strip().toLowerCase(Locale.ROOT)` in ProductService; map `coverImageUrl` in every product SELECT/INSERT/UPDATE.

```java
static String validateCoverImageUrl(String raw) {
    if (raw == null || raw.isBlank()) return null;
    if (raw.length() > 2048) throw coverUrlInvalid();
    URI uri = URI.create(raw.strip());
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || uri.getUserInfo() != null) throw coverUrlInvalid();
    return uri.normalize().toASCIIString();
}
```

Use `ShopCategories.requireSupported(shop.category())` and always persist the owned shop category, ignoring no client-selected alternative.

- [ ] **Step 4: Run focused and full server tests**

Run:

```powershell
mvn -pl vcampus-server -am -Dtest=ProductServiceTest,AccessShopRepositoryTest test
mvn -pl vcampus-server -am test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- vcampus-database/schema/050_shop.sql vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test/java/edu/seu/vcampus/server/shop
git commit -m "feat(shop): persist grouped product covers"
```

### Task 4: Correct payment history and product-detail behavior

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/PaymentResultPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductDetailPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/PurchasePanelsTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigatorTest.java`

**Interfaces:**
- Consumes: existing `ShopNavigator.reset`, `open`, and `back`.
- Produces: safe Home → My history and SKU-name selection with committed quantity input.

- [ ] **Step 1: Write failing UI tests**

```java
@Test void paidOrdersLeavesHomeAsBackTarget() {
    panel.openPaidOrders();
    assertThat(navigator.current()).contains(new ShopRoute.My());
    navigator.back();
    assertThat(navigator.current().orElseThrow()).isInstanceOf(ShopRoute.Home.class);
}

@Test void typedQuantityAboveStockDoesNotSendAddRequest() {
    spinnerEditorText(panel, "quantity", "999");
    click(panel, "add-to-cart");
    assertThat(client.addCommands()).isEmpty();
    assertThat(stateText(panel)).contains("库存");
}
```

Also assert the combo box visible values are `黑色 0.5mm` rather than SKU IDs.

- [ ] **Step 2: Run focused client tests and verify red**

Run: `mvn -pl vcampus-client -am -Dtest=PurchasePanelsTest,ShopNavigatorTest test`

Expected: FAIL on history reset, SKU display, and uncommitted editor text.

- [ ] **Step 3: Implement safe navigation and typed SKU items**

Use an internal combo item:

```java
private record SkuChoice(String skuId, String label) {
    @Override public String toString() { return label; }
}
```

Before reading the spinner value, call `quantity.commitEdit()`, catch `ParseException`, then verify `1 <= value <= selected.availableQuantity()`. `openPaidOrders()` must call `reset(homeRoute())` followed by `open(new ShopRoute.My())`.

- [ ] **Step 4: Run focused and client tests**

Run:

```powershell
mvn -pl vcampus-client -am -Dtest=PurchasePanelsTest,ShopNavigatorTest test
mvn -pl vcampus-client -am test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/PaymentResultPanel.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductDetailPanel.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/PurchasePanelsTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigatorTest.java
git commit -m "fix(shop): correct paid-order return and sku quantity"
```

### Task 5: Make search filters persistent and sorting immediate

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/SearchViewState.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductSearchPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/CatalogPanelsTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigatorTest.java`

**Interfaces:**
- Produces: search state with `searched` and scroll position; filter controls derive visibility from `searched` and have no collapse state.

- [ ] **Step 1: Write failing interaction tests**

Use the existing Swing component-name helpers and fake client to implement them concretely:

```java
@Test void successfulSearchKeepsFilterControlsVisible() {
    panel.search(new SearchViewState(query(), false, 0));
    client.completeSearch(page(summary("中性笔", "2.80")));
    awaitEdt();
    assertThat(component(panel, "search.filters").isVisible()).isTrue();
}

@Test void categoryAndPricesApplyOnlyWhenFilterButtonClicked() {
    completeFirstSearch();
    select(panel, "search.category", "文具");
    text(panel, "search.min-price", "2.00");
    assertThat(client.searchQueries()).hasSize(1);
    click(panel, "search.filter");
    assertThat(client.searchQueries().getLast().category()).isEqualTo("文具");
}

@Test void changingSortImmediatelySearchesFirstPage() {
    completeFirstSearch();
    select(panel, "search.sort", ProductSortMode.PRICE_ASC);
    assertThat(client.searchQueries().getLast().pageNumber()).isZero();
}

@Test void restoringStateDoesNotTriggerAnExtraSearch() {
    panel.search(new SearchViewState(queryWithSort(ProductSortMode.PRICE_ASC), true, 42));
    assertThat(client.searchQueries()).hasSize(1);
}
```

- [ ] **Step 2: Run focused tests and verify red**

Run: `mvn -pl vcampus-client -am -Dtest=CatalogPanelsTest,ShopNavigatorTest test`

Expected: FAIL because `filterToggle` still collapses and sort has no direct listener.

- [ ] **Step 3: Implement the confirmed state machine**

Remove `filtersExpanded` from `SearchViewState`. Keep controls hidden until the first successful response; then keep them visible. Populate category controls from `ShopCategories.ALL`. Bind category and price only to the `筛选` button. Bind sort to `ItemEvent.SELECTED`, suppress the listener inside a `restoring` boolean, and replace the current route with page zero.

- [ ] **Step 4: Run client tests and commit**

Run: `mvn -pl vcampus-client -am test`

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/SearchViewState.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductSearchPanel.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop
git commit -m "fix(shop): make filters persistent and sorting immediate"
```

### Task 6: Add replaceable image loading and product-card grid

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/ProductImageLoader.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/HttpsProductImageLoader.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/ProductCardRenderer.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/DefaultProductCardRenderer.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/ProductGridPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ShopHomePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductSearchPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/BuyerShopPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductDetailPanel.java`
- Remove after callers migrate: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductCardsPanel.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/catalog/ProductGridPanelTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/catalog/HttpsProductImageLoaderTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/CatalogPanelsTest.java`

**Interfaces:**
- Produces: `CompletableFuture<ImageIcon> ProductImageLoader.load(String coverImageUrl, String category, Dimension target)` and `void close()`.
- Produces: `JComponent ProductCardRenderer.render(ProductSummary product, ImageIcon image, Runnable openDetail)`.
- Produces: `ProductGridPanel.showProducts(List<ProductSummary>)`, `visibleProductNames()`, and `dispose()`.

- [ ] **Step 1: Write failing renderer/grid tests with a fake loader**

```java
@Test void rendersOneClickableCardPerProductType() {
    grid.showProducts(List.of(summary("中性笔", "2.80"), summary("笔记本", "6.90")));
    assertThat(grid.visibleProductNames()).containsExactly("中性笔", "笔记本");
    click(grid, "product-card.p1");
    assertThat(opened).containsExactly("p1");
}

@Test void injectedRendererCanChangeCardWithoutChangingNavigation() {
    ProductGridPanel grid = new ProductGridPanel(fakeImages, customRenderer, opened::add);
    grid.showProducts(List.of(summary("p1", "中性笔", "2.80")));
    assertThat(component(grid, "custom-card.p1")).isNotNull();
    click(grid, "custom-card.p1");
    assertThat(opened).containsExactly("p1");
}
```

- [ ] **Step 2: Write failing image policy tests**

Use an injected `ImageSource` seam so tests do not access the Internet. Assert HTTPS success, timeout/oversize/decode failure fallback, bounded cache reuse, and EDT completion.

- [ ] **Step 3: Run focused tests and verify red**

Run: `mvn -pl vcampus-client -am -Dtest=ProductGridPanelTest,HttpsProductImageLoaderTest,CatalogPanelsTest test`

Expected: FAIL because the catalog package does not exist.

- [ ] **Step 4: Implement the interfaces and responsive grid**

Use a wrapping `JPanel` whose column count is recomputed from available width and a configurable minimum card width. Card content is cover image, generic product name, and `¥<minimumPrice> 起`. Image work runs on a bounded executor; only `SwingUtilities.invokeLater` mutates labels. Limit download bytes and decoded dimensions before scaling; maintain a bounded LRU cache and category fallback icons.

- [ ] **Step 5: Replace all three buyer catalog callers**

Inject the same renderer/loader factory through `ShopPageCoordinator.BuyerPageFactory`. Use the same loader for the product-detail cover. Preserve route callbacks, paging, scroll capture, request fencing, state components, and component names used by tests.

- [ ] **Step 6: Run focused and full client tests**

Run:

```powershell
mvn -pl vcampus-client -am -Dtest=ProductGridPanelTest,HttpsProductImageLoaderTest,CatalogPanelsTest,ShopUiTest test
mvn -pl vcampus-client -am test
```

Expected: PASS with no HTTP request in deterministic UI tests.

- [ ] **Step 7: Commit**

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop vcampus-client/src/test/java/edu/seu/vcampus/client/shop
git commit -m "feat(shop): render catalog as image card grid"
```

### Task 7: Enforce buyer-role and self-purchase restrictions

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/BuyerGuard.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/ShopRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/AccessShopRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/CartService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/CheckoutService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/payment/SimulatedPaymentService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/CartServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/CheckoutServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/payment/SimulatedPaymentServiceTest.java`

**Interfaces:**
- Produces: `BuyerGuard.requireBuyer(ShopUser)` and `BuyerGuard.requireDifferentOwner(String buyerId, String ownerUserId)`.
- Produces repository lookup `Optional<String> findShopOwnerBySku(Connection, String skuId)`.

- [ ] **Step 1: Write failing server authorization tests**

```java
@Test void administratorCannotAddToCart() {
    assertShopCode(() -> cart.addToCart("admin-token", add("sku-1", 1)),
            ShopErrorCode.SHOP_BUYER_FORBIDDEN);
}

@Test void ownerCannotAddOwnSkuToCart() {
    assertShopCode(() -> cart.addToCart("owner-token", add("owned-sku", 1)),
            ShopErrorCode.SHOP_SELF_PURCHASE_FORBIDDEN);
}

@Test void checkoutRechecksOwnerEvenForExistingCartRows() {
    assertShopCode(() -> checkout.checkout("owner-token", checkout("cart-item")),
            ShopErrorCode.SHOP_SELF_PURCHASE_FORBIDDEN);
    assertThat(reserved("owned-sku")).isZero();
}

@Test void paymentRejectsAdministratorSession() {
    assertShopCode(() -> payment.simulatePayment("admin-token", payment("payment-1")),
            ShopErrorCode.SHOP_BUYER_FORBIDDEN);
    assertThat(stock("sku-1")).isEqualTo(10);
}
```

- [ ] **Step 2: Run tests and verify red**

Run: `mvn -pl vcampus-server -am -Dtest=CartServiceTest,CheckoutServiceTest,SimulatedPaymentServiceTest test`

Expected: FAIL because active administrators currently pass buyer checks.

- [ ] **Step 3: Add the guard at every buyer mutation boundary**

```java
static ShopUser requireBuyer(ShopUser user) {
    if (!user.active() || user.kind() == ShopUserKind.ADMINISTRATOR)
        throw error(ShopErrorCode.SHOP_BUYER_FORBIDDEN, "Buyer role required");
    return user;
}
```

Cart add/update, checkout, and payment must invoke it. Cart add and checkout must compare the buyer ID with the selected product's `ownerUserId` inside the same transaction.

- [ ] **Step 4: Run server tests and commit**

Run: `mvn -pl vcampus-server -am test`

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test/java/edu/seu/vcampus/server/shop
git commit -m "feat(shop): enforce buyer permission boundaries"
```

### Task 8: Buyer-catalog checkpoint

**Files:**
- Test only; no production change unless a failing Shop test identifies a scoped regression.

- [ ] **Step 1: Run the three Shop module suites**

```powershell
mvn -pl vcampus-common test
mvn -pl vcampus-server -am test
mvn -pl vcampus-client -am test
git diff --check
git status --short
```

Expected: all tests PASS; only later-plan work, if any, is uncommitted; `logs/` remains untracked.

- [ ] **Step 2: Review checkpoint**

Inspect `git log --oneline origin/SHOP..HEAD` and manually verify one buyer flow before starting the application plan.
