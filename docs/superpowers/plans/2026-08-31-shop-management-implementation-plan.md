# Shop Seller and Admin Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Execute inline in the primary session and stop at each review checkpoint.

**Goal:** Deliver owner-scoped shop/product/order management and administrator-scoped cross-shop product management with server-enforced ownership and audit logging.

**Architecture:** Common defines management projections distinct from buyer catalog DTOs. Repository queries aggregate SKU inventory and order snapshots. Seller services derive the owned shop from session; admin services accept a target shop only after session-admin authorization. Separate handlers, client ports, and Swing workspaces expose these capabilities.

**Tech Stack:** Java 21, Swing, Java serialization, CompletableFuture, JDBC/UCanAccess, JUnit 5, AssertJ, Maven.

**Spec:** `docs/superpowers/specs/2026-08-31-vcampus-shop-seller-admin-design.md`

## Global Constraints

- Sellers can mutate only their own active shop; suspended sellers retain read-only profile/product/order access.
- Shop category is locked after approval; all products inherit it.
- A product must have at least one active SKU with positive stock before activation.
- Stock cannot be reduced below reserved quantity.
- Product deletion is `INACTIVE`, preserving history.
- Seller orders are read-only and scoped to the owned shop.
- Administrators select a target shop and may manage its products, but remain unable to buy.

---

### Task 1: Define management projections and commands

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductManagementQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ProductManagementSummary.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerOrderQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerOrderItemView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerOrderView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerOrderHistory.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/AdminCreateProductCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/AdminUpdateProductCommand.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/AdminChangeProductStatusCommand.java`
- Create: `vcampus-common/src/test/java/edu/seu/vcampus/common/shop/ManagementContractTest.java`

**Interfaces:**
- `ProductManagementSummary`: product ID/name/status, SKU count, minimum price, total stock, reserved stock, sales count, row version.
- `ProductManagementQuery`: target shop ID, status, keyword, page number, page size.
- Seller orders include order ID/number, buyer identifier, shop, items, amount, paid time, and status.
- Admin commands wrap `shopId` plus the existing product command data and expected versions.

- [ ] **Step 1: Write failing serialization and defensive-copy tests**

Construct every record with two SKU/order items, round-trip it, mutate the source list, and assert the record retains its original immutable list.

- [ ] **Step 2: Run test and verify red**

Run: `mvn -pl vcampus-common -Dtest=ManagementContractTest test`

- [ ] **Step 3: Implement records with exact money and count types**

Use `BigDecimal` for money, `long` for inventory/sales/version, `Instant` for paid time, and existing `ProductStatus`/`OrderStatus` enums. Use `List.copyOf` in compact constructors.

- [ ] **Step 4: Run Common tests and commit**

Run: `mvn -pl vcampus-common test`

```powershell
git add -- vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-common/src/test/java/edu/seu/vcampus/common/shop
git commit -m "feat(shop): define management contracts"
```

### Task 2: Add management and seller-order repository queries

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/ShopRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/AccessShopRepository.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/repository/AccessShopRepositoryTest.java`

**Interfaces:**
- Produces `PageResult<ProductManagementSummary> searchManagedProducts(Connection, ProductManagementQuery)`.
- Produces `List<SellerOrderView> findOrdersByShop(Connection, String shopId, SellerOrderQuery)`.

- [ ] **Step 1: Write failing aggregate-query tests**

Seed two products with multiple SKU rows and reservations. Assert SKU count, `MIN(unitPrice)`, `SUM(stockQuantity)`, `SUM(reservedQuantity)`, product sales, stable pagination, and no duplicate product. Seed orders in two shops and assert only the requested shop appears with snapshot names and paid-time ordering.

- [ ] **Step 2: Run repository tests and verify red**

Run: `mvn -pl vcampus-server -am '-Dtest=AccessShopRepositoryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

- [ ] **Step 3: Implement Access-compatible aggregate SQL**

Group by every non-aggregate selected product column. Load seller order headers by shop, then details by returned order IDs to avoid cross-shop joins and preserve orders whose product is now inactive.

- [ ] **Step 4: Run repository tests and commit**

Run: `mvn -pl vcampus-server -am '-Dtest=AccessShopRepositoryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository vcampus-server/src/test/java/edu/seu/vcampus/server/shop/repository/AccessShopRepositoryTest.java
git commit -m "feat(shop): query managed products and seller orders"
```

### Task 3: Harden seller product and profile services

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/SellerService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ProductService.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/SellerOrderService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ProductServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ShopOwnershipTest.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/SellerOrderServiceTest.java`

**Interfaces:**
- Adds `searchOwnedProducts(String sessionToken, ProductManagementQuery query)`.
- Adds `getOwnedOrders(String sessionToken, SellerOrderQuery query)`.
- Keeps `updateShop`, `createProduct`, `updateProduct`, and `changeProductStatus` seller-owned.

- [ ] **Step 1: Write failing seller-boundary tests**

Assert shop profile rename rechecks global normalized-name uniqueness; profile category remains the approved category even if a command supplies another value; product category is replaced by the owned shop category; owner cannot mutate another shop; suspended owner can read profile/products/orders but cannot write; duplicate normalized product name fails; SKU stock below reserved fails; activation without an active positive-stock SKU fails; omitted existing SKU becomes inactive only when its reserved quantity is zero.

- [ ] **Step 2: Run service tests and verify red**

Run: `mvn -pl vcampus-server -am '-Dtest=ProductServiceTest,ShopOwnershipTest,SellerOrderServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

- [ ] **Step 3: Split read and write shop lookup paths**

`SellerService.getOwnedShop` remains readable for suspended shops. Product writes call `requireOwnedActiveShop`; product/order list calls require ownership but permit suspended state. Lock product plus each existing/new SKU key before mutation and keep version checks inside one transaction.

- [ ] **Step 4: Run focused and full server suites**

Run:

```powershell
mvn -pl vcampus-server -am '-Dtest=ProductServiceTest,ShopOwnershipTest,SellerOrderServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl vcampus-server -am test
```

- [ ] **Step 5: Commit**

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service
git commit -m "feat(shop): enforce seller management boundaries"
```

### Task 4: Add administrator cross-shop product service

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/AdminProductService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/logging/ShopBusinessLogger.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/AdminProductServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/logging/ShopBusinessLoggerTest.java`

**Interfaces:**
- `searchProducts(String sessionToken, ProductManagementQuery)`.
- `createProduct(String sessionToken, AdminCreateProductCommand)`.
- `updateProduct(String sessionToken, AdminUpdateProductCommand)`.
- `changeStatus(String sessionToken, AdminChangeProductStatusCommand)`.

- [ ] **Step 1: Write failing admin-product tests**

Assert active admin may manage each selected shop, category is inherited from target shop, regular users are forbidden, target shop must exist, stock/reservation and activation invariants match seller service, and every write logs actor/shop/product/change summary.

- [ ] **Step 2: Run focused tests and verify red**

Run: `mvn -pl vcampus-server -am '-Dtest=AdminProductServiceTest,ShopBusinessLoggerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

- [ ] **Step 3: Implement shared Shop-only mutation policy**

Extract package-private validation/mutation helpers from `ProductService` only where seller and admin behavior is identical. Keep authorization and target-shop resolution in their respective public services. Do not expose an `isAdmin` boolean from the client.

- [ ] **Step 4: Run server tests and commit**

Run: `mvn -pl vcampus-server -am test`

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test/java/edu/seu/vcampus/server/shop
git commit -m "feat(shop): add administrator product management"
```

### Task 5: Expose management handlers and client ports

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/SellerShopHandlers.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/AdminShopHandlers.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/SellerShopClientPort.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/AdminShopClientPort.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler/SellerShopHandlersTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler/AdminShopHandlersTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/service/ShopClientServiceTest.java`

**Interfaces:**
- Seller commands: `SHOP_SELLER_GET_SHOP`, `SHOP_SELLER_UPDATE_SHOP`, `SHOP_SELLER_SEARCH_PRODUCTS`, `SHOP_SELLER_CREATE_PRODUCT`, `SHOP_SELLER_UPDATE_PRODUCT`, `SHOP_SELLER_CHANGE_PRODUCT_STATUS`, `SHOP_SELLER_GET_ORDERS`.
- Admin commands: `SHOP_ADMIN_SEARCH_PRODUCTS`, `SHOP_ADMIN_CREATE_PRODUCT`, `SHOP_ADMIN_UPDATE_PRODUCT`, `SHOP_ADMIN_CHANGE_PRODUCT_STATUS`.

- [ ] **Step 1: Write failing protocol mapping tests**

For every command assert session propagation, body type, typed response, deduplication for writes, read isolation, and stable error code mapping. A seller query containing another shop ID must still resolve to the session owner's shop.

- [ ] **Step 2: Run handler/client tests and verify red**

Run:

```powershell
mvn -pl vcampus-server -am '-Dtest=SellerShopHandlersTest,AdminShopHandlersTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl vcampus-client -am '-Dtest=ShopClientServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

- [ ] **Step 3: Register and implement mappings**

Reuse `ShopHandlerSupport` and `ShopClientService.send`. Seller service request DTOs do not accept owner IDs. Admin DTOs accept target shop IDs but authorization is taken only from session.

- [ ] **Step 4: Run server/client tests and commit**

Run:

```powershell
mvn -pl vcampus-server -am test
mvn -pl vcampus-client -am test
```

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service vcampus-client/src/test/java/edu/seu/vcampus/client/shop/service
git commit -m "feat(shop): expose seller and admin management"
```

### Task 6: Build the seller workspace

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SellerWorkspacePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ShopProfilePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ProductManagementPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ProductEditorPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SellerOrdersPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/SellerWorkspacePanelTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/ProductManagementPanelTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/SellerOrdersPanelTest.java`

**Interfaces:**
- Workspace tabs: profile/status, products, orders.
- Product table columns: name, status, SKU count, minimum price, total stock, reserved stock, sales.
- Product editor owns generic fields plus a multi-row SKU editor.

- [ ] **Step 1: Write failing workspace tests**

Assert profile category is read-only; suspension reason is shown; suspended write controls are disabled while order/product views load; clicking a product row loads its editor; SKU rows preserve IDs and versions; create/update/status actions call the seller port; orders show only returned shop data and expand details.

- [ ] **Step 2: Run focused tests and verify red**

Run: `mvn -pl vcampus-client -am '-Dtest=SellerWorkspacePanelTest,ProductManagementPanelTest,SellerOrdersPanelTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

- [ ] **Step 3: Implement small panels with shared state seams**

Each tab owns one `LatestRequest`. Keep `ProductEditorPanel` independent of transport: expose `load(ProductView)`, `CreateProductCommand createCommand()`, and `UpdateProductCommand updateCommand()`. Validate required text, HTTPS cover, SKU names/prices/stocks, and reserved floor before invoking the port; display server errors unchanged through `ShopUiErrors`.

- [ ] **Step 4: Run client tests and commit**

Run: `mvn -pl vcampus-client -am test`

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller
git commit -m "feat(shop): add seller management workspace"
```

### Task 7: Build the administrator product workspace

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/AdminProductManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ShopAdminPanel.java`
- Reuse: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ProductEditorPanel.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/admin/AdminProductManagementPanelTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`

**Interfaces:**
- Admin selects `ShopAdminSummary`; product queries and commands always carry that selected `shopId`.
- Reuses editor field model while dispatching through `AdminShopClientPort`.

- [ ] **Step 1: Write failing admin UI tests**

Assert no product request before selecting a shop; selecting a shop loads its products; switching shops fences the older response; add/edit/status actions carry the selected shop ID; category displays selected shop category; losing admin authorization routes through the standard error/session behavior.

- [ ] **Step 2: Run focused tests and verify red**

Run: `mvn -pl vcampus-client -am '-Dtest=AdminProductManagementPanelTest,ShopUiTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

- [ ] **Step 3: Implement selection-fenced admin management**

Use separate `LatestRequest` instances for shops and products. Capture selected shop ID with each mutation callback and discard completions after selection changes. Reuse SKU/editor components through injected submit functions rather than importing seller authorization logic.

- [ ] **Step 4: Run client tests and commit**

Run: `mvn -pl vcampus-client -am test`

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/admin vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java
git commit -m "feat(shop): add administrator product workspace"
```

### Task 8: Management checkpoint

- [ ] **Step 1: Run full module suites**

```powershell
mvn -pl vcampus-common test
mvn -pl vcampus-server -am test
mvn -pl vcampus-client -am test
git diff --check
git status --short
```

Expected: all tests PASS; `logs/` remains untracked; seller/admin operations are covered through service, handler, client-port, and Swing tests.
