# Shop Demo and Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Execute inline in the primary session and stop at each review checkpoint.

**Goal:** Deliver deterministic five-category Demo data, four role-testing accounts, complete runtime wiring, end-to-end coverage, documentation, and portable Windows package verification.

**Architecture:** Demo catalog seeds exactly 100 generic products across five active shops and attaches multiple SKU rows and HTTPS cover URLs. Demo authentication uses existing User services with four fixed accounts. Runtime composes all Shop services and handlers, while end-to-end tests use real Socket requests and deterministic database assertions.

**Tech Stack:** Java 21, Swing, JDBC/UCanAccess, existing User password hashing/session services, JUnit 5, AssertJ, Maven, PowerShell, batch launchers.

**Spec:** `docs/superpowers/specs/2026-08-31-vcampus-shop-seller-admin-design.md`

## Global Constraints

- Initial Demo contains five ACTIVE shops and exactly 100 generic products: 10 文具, 30 图书, 45 生活用品, 5 药品, 10 其他.
- Every product has at least one SKU; representative products have multiple meaningful SKU variants.
- Fixed logins are `DEMO_BUYER`, `DEMO_OTHER_BUYER`, `DEMO_TEACHER`, `DEMO_ADMIN`; every password is `123456`.
- `DEMO_TEACHER` starts with a rejected application; buyers have isolated paid-order data; admin is management-only.
- Remote image failure must fall back locally; automated tests do not require Internet access.
- Package retains one-click server and client batch launchers and includes updated instructions.

---

### Task 1: Rebuild the deterministic five-category catalog

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopDemoCatalog.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopDemo.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabase.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo/ShopDemoTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabaseTest.java`

**Interfaces:**
- `ShopDemoCatalog.products()` returns exactly 100 `ProductSeed` values.
- `ProductSeed` carries generic name, description, category, cover URL, deterministic sales, and one-or-more SKU seeds.

- [ ] **Step 1: Write failing exact-count and grouping tests**

```java
assertThat(countProducts()).isEqualTo(100);
assertThat(countProducts("文具")).isEqualTo(10);
assertThat(countProducts("图书")).isEqualTo(30);
assertThat(countProducts("生活用品")).isEqualTo(45);
assertThat(countProducts("药品")).isEqualTo(5);
assertThat(countProducts("其他")).isEqualTo(10);
assertThat(countDistinctProductNamesPerShop()).isEqualTo(100);
assertThat(skuNames("中性笔")).contains("黑色 0.5mm", "蓝色 0.5mm");
```

Also assert five active shops, every product has a valid HTTPS URL, every product has a SKU, SKU IDs/names are unique within product, and sales/price/stock values support ordering and filters.

- [ ] **Step 2: Run Demo database tests and verify red**

Run: `mvn -pl vcampus-server -am -Dtest=ShopDemoTest,ShopAuthDemoDatabaseTest test`

Expected: FAIL because current distribution is four categories and variant-like names remain product rows.

- [ ] **Step 3: Define five explicit product lists and SKU factories**

Keep the 100 generic names in reviewed immutable lists. Use deterministic IDs, prices, stock, sales, and HTTPS cover URLs. Assign “校园综合店” to `其他`. For multi-SKU products, keep color/size/package in `skuName`; keep `productName` generic.

- [ ] **Step 4: Update both Demo initializers from the same catalog source**

`ShopDemo` and `ShopAuthDemoDatabase` must consume `ShopDemoCatalog` rather than duplicate counts or names. Database inserts supply normalized product name, cover URL, and all SKU fields.

- [ ] **Step 5: Run focused tests and commit**

Run: `mvn -pl vcampus-server -am -Dtest=ShopDemoTest,ShopAuthDemoDatabaseTest test`

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo
git commit -m "feat(shop-demo): seed five-category product catalog"
```

### Task 2: Seed fixed accounts, applications, and isolated orders

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabase.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabaseTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthEndToEndTest.java`

**Interfaces:**
- Produces four fixed User rows through existing password hashing-compatible data.
- Produces one rejected teacher application with reason and statement.
- Produces paid orders isolated between `DEMO_BUYER` and `DEMO_OTHER_BUYER`.

- [ ] **Step 1: Write failing database assertions**

Assert exactly four fixed login IDs with expected roles; authenticate each with `123456`; teacher application is REJECTED with reason and editable material; buyer order queries return only their own paid rows; admin has no cart/order creation fixture.

- [ ] **Step 2: Run focused tests and verify red**

Run: `mvn -pl vcampus-server -am -Dtest=ShopAuthDemoDatabaseTest test`

- [ ] **Step 3: Update deterministic seed data**

Use the existing User `PasswordHasher` format or the same known encoded fixture path already used by Demo login. Keep account IDs stable so order/application foreign keys remain deterministic. Seed application and orders through Shop-owned SQL only after User rows exist.

- [ ] **Step 4: Run server and login end-to-end tests**

Run:

```powershell
mvn -pl vcampus-server -am -Dtest=ShopAuthDemoDatabaseTest test
mvn -pl vcampus-client -am -Dtest=ShopAuthEndToEndTest test
```

- [ ] **Step 5: Commit**

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabase.java vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabaseTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthEndToEndTest.java
git commit -m "feat(shop-demo): seed role and workflow fixtures"
```

### Task 3: Compose all Shop services and role-aware UI

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoRuntime.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMain.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopUiInstaller.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabaseTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMainTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`

**Interfaces:**
- Runtime registers Buyer, Seller, and Admin Shop handlers against one repository/transaction/lock/session composition.
- Client constructs one `ShopClientService` and passes its three capability interfaces plus authenticated `UserView` to Shop UI.

- [ ] **Step 1: Write failing runtime registration tests**

After login, send one command from each surface and assert it is registered: buyer home, seller application lookup, admin application search. Assert student admin command returns `AUTH_FORBIDDEN`, and admin cart-add returns `SHOP_BUYER_FORBIDDEN`.

- [ ] **Step 2: Run focused tests and verify red**

Run: `mvn -pl vcampus-client -am -Dtest=ShopAuthDemoClientMainTest,ShopAuthEndToEndTest,ShopUiTest test`

- [ ] **Step 3: Wire services and handlers**

Instantiate `SellerApplicationService`, `SellerService`, `ProductService`, `SellerOrderService`, `ShopAdminService`, and `AdminProductService` using the same repository, transaction manager, lock manager, user adapter, and clock. Register Seller/Admin handlers after User handlers and before creating `SocketServer`.

- [ ] **Step 4: Wire role-aware client pages**

Pass `UserView` and capability-specific ports through `ShopUiInstaller`. Admin sees management routes and buyer read-only catalog but no cart/checkout actions. Seller state is loaded from Shop, not added to `UserView`.

- [ ] **Step 5: Run client/server suites and commit**

Run:

```powershell
mvn -pl vcampus-server -am test
mvn -pl vcampus-client -am test
```

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoRuntime.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo vcampus-client/src/test/java/edu/seu/vcampus/client/shop
git commit -m "feat(shop-demo): wire seller and admin workflows"
```

### Task 4: Add real-Socket workflow coverage

**Files:**
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthEndToEndTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopSellerAdminEndToEndTest.java`

**Interfaces:**
- Consumes real Demo runtime and `ShopClientService`.
- Produces end-to-end evidence for authentication, authorization, application, approval, management, and buyer flow.

- [ ] **Step 1: Add end-to-end scenarios one at a time**

Implement independent database-per-test scenarios:

1. `DEMO_BUYER` logs in, saves/submits an application, admin approves, buyer reloads and sees owned ACTIVE shop.
2. `DEMO_TEACHER` edits rejected application, resubmits, admin rejects with a new reason, teacher sees it.
3. Approved owner creates generic “中性笔” with two SKU rows, activates it, and sees one management/catalog item.
4. Owner cannot add own SKU; other buyer can add and pay; seller order query sees that order only.
5. Admin suspends shop; catalog hides it; seller writes fail while reads remain; admin resumes it.
6. Admin edits target-shop SKU price/stock and cannot add any product to cart.

- [ ] **Step 2: Run each scenario while developing**

Run: `mvn -pl vcampus-client -am -Dtest=ShopSellerAdminEndToEndTest test`

Expected: each new scenario fails before its missing wiring/fix and passes after the smallest scoped correction.

- [ ] **Step 3: Run all client tests and commit**

Run: `mvn -pl vcampus-client -am test`

```powershell
git add -- vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo
git commit -m "test(shop-demo): cover seller and admin workflows"
```

### Task 5: Update team instructions and portable package

**Files:**
- Modify: `vcampus-database/demo/SHOP_AUTH_DEMO.md`
- Modify: `vcampus-database/demo/SHOP_AUTH_DEMO_TEAM_TESTING.md`
- Modify: `vcampus-distribution/SHOP_AUTH_DEMO_PACKAGE_USAGE.md`
- Modify: `vcampus-distribution/templates/shop-auth-demo/使用说明.txt`
- Modify: `vcampus-distribution/scripts/build-shop-auth-demo-package.ps1`
- Test: `vcampus-distribution/scripts/tests/build-shop-auth-demo-package.tests.ps1`
- Test: `vcampus-distribution/scripts/tests/start-shop-auth-demo-scripts.tests.ps1`

**Interfaces:**
- Documents four accounts and password `123456`.
- Documents one-click `启动服务端.bat` then `启动客户端.bat` flow and role-specific manual scenarios.
- Package contains runtime JARs/config/database/default images/scripts/docs, not source code or logs.

- [ ] **Step 1: Write failing package assertions**

Assert the built ZIP contains both batch files, updated instructions, Demo database, required runtime artifacts, and category default images; assert it excludes `logs`, Maven target intermediates, and source trees.

- [ ] **Step 2: Run PowerShell tests and verify red**

Run:

```powershell
& ./vcampus-distribution/scripts/tests/build-shop-auth-demo-package.tests.ps1
& ./vcampus-distribution/scripts/tests/start-shop-auth-demo-scripts.tests.ps1
```

- [ ] **Step 3: Update documentation with exact manual flows**

Include login matrix; buyer search/SKU/cart/payment; student application; teacher resubmission; admin approval/suspension/product management; seller product/order management; expected permission failures; image fallback behavior; port-in-use troubleshooting.

- [ ] **Step 4: Build and inspect the ZIP**

Run:

```powershell
& ./vcampus-distribution/scripts/build-shop-auth-demo-package.ps1
Get-FileHash ./target/shop-auth-demo-release/*/vCampus-Shop-Demo.zip -Algorithm SHA256
```

Expected: one portable ZIP is produced under `target`, which remains ignored and uncommitted.

- [ ] **Step 5: Commit docs/scripts only**

```powershell
git add -- vcampus-database/demo vcampus-distribution
git commit -m "docs(shop-demo): document seller and admin package"
```

### Task 6: Final verification and handoff

**Files:**
- Verification only.

- [ ] **Step 1: Run complete Maven verification**

```powershell
mvn -pl vcampus-common clean verify
mvn -pl vcampus-server -am test
mvn -pl vcampus-client -am test
```

Record the exact test totals from each Surefire summary.

- [ ] **Step 2: Run script behavior and AST checks**

```powershell
& ./vcampus-distribution/scripts/tests/build-shop-auth-demo-package.tests.ps1
& ./vcampus-distribution/scripts/tests/start-shop-auth-demo-scripts.tests.ps1
$scripts = Get-ChildItem ./vcampus-distribution -Recurse -Filter *.ps1
$parseErrors = @()
foreach ($script in $scripts) {
    [System.Management.Automation.Language.Parser]::ParseFile(
        $script.FullName, [ref]$null, [ref]$parseErrors) | Out-Null
}
if ($parseErrors.Count -ne 0) { throw ($parseErrors | Out-String) }
```

- [ ] **Step 3: Check repository hygiene**

```powershell
git diff --check
git status --short --branch
git log --oneline origin/SHOP..HEAD
```

Expected: no whitespace errors; `logs/` is the only intentional untracked runtime directory; no ZIP or generated database is staged.

- [ ] **Step 4: Perform manual smoke test from the ZIP**

Start the server batch file, then the client batch file. Log in once as buyer, teacher, admin, and newly approved seller; execute the role-specific happy path and one forbidden operation per role. Stop processes through their normal windows after recording results.

- [ ] **Step 5: Prepare final local-only report**

Report commit list, test totals, package path and SHA-256, manual smoke results, remaining limitations, and startup credentials. Do not push.
