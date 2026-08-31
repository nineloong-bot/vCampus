# Shop Application and Approval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Execute inline in the primary session and stop at each review checkpoint.

**Goal:** Deliver session-authorized shop applications, rejection/resubmission, approval, and shop suspension/resumption through Common, Server, Socket, Client, and Swing UI.

**Architecture:** Existing seller application and admin services remain the domain core but receive session tokens for every operation. Repository-level normalized-name checks and optimistic versions protect approval. Seller and admin client ports expose separate capabilities, while Shop routes and “我的” select actions from the authenticated role and application state.

**Tech Stack:** Java 21, Swing, Java serialization, CompletableFuture, JDBC/UCanAccess, JUnit 5, AssertJ, Maven.

**Spec:** `docs/superpowers/specs/2026-08-31-vcampus-shop-seller-admin-design.md`

## Global Constraints

- Students and teachers may own at most one application and one shop.
- Administrators are derived from the authenticated User session and cannot apply.
- Application fields are shop name, category, description, contact, and application statement.
- Pending applications are read-only; rejected applications retain the reason and may be edited and resubmitted.
- Approved shop names are globally unique after strip and lowercase normalization.
- Approval, rejection, suspension, and resumption use optimistic versions and Shop business logs.
- No User, Foundation, Socket, Router, transaction-framework, or public-network implementation changes.

---

### Task 1: Extend application and administrative Common contracts

**Files:**
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerApplicationView.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SaveSellerDraftCommand.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerApplicationQuery.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopView.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopAdminQuery.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopAdminSummary.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopErrorCode.java`
- Create: `vcampus-common/src/test/java/edu/seu/vcampus/common/shop/SellerApplicationContractTest.java`

**Interfaces:**
- Produces `applicationStatement` in draft/view records.
- Produces `ShopAdminQuery(String keyword, ShopStatus status, int pageNumber, int pageSize)`.
- Produces `ShopAdminSummary(String shopId, String ownerUserId, String shopName, String category, ShopStatus status, long productCount, long rowVersion)`.
- Produces error symbols `SHOP_NAME_EXISTS` and `SHOP_CONCURRENT_MODIFICATION`.

- [ ] **Step 1: Write failing serialization and category tests**

```java
@Test void draftCarriesApplicationStatement() {
    SaveSellerDraftCommand command = new SaveSellerDraftCommand(
            null, "校园文具店", "服务师生", "文具", "13800000000", "诚信经营计划", 0);
    assertThat(roundTrip(command).applicationStatement()).isEqualTo("诚信经营计划");
}
```

Add round-trip assertions for `ShopAdminQuery` and `ShopAdminSummary`.

- [ ] **Step 2: Run the Common test and verify red**

Run: `mvn -pl vcampus-common -Dtest=SellerApplicationContractTest test`

Expected: FAIL on missing record components.

- [ ] **Step 3: Add the immutable DTOs and error codes**

Keep record component order identical between constructors, serialization tests, server mappers, and client fixtures. Defensively validate page numbers and sizes at service entry rather than record construction.

- [ ] **Step 4: Run Common tests and commit**

Run: `mvn -pl vcampus-common test`

```powershell
git add -- vcampus-common/src/main/java/edu/seu/vcampus/common/shop vcampus-common/src/test/java/edu/seu/vcampus/common/shop
git commit -m "feat(shop): extend seller application contracts"
```

### Task 2: Persist application statements and normalized shop names

**Files:**
- Modify: `vcampus-database/schema/050_shop.sql`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/domain/SellerApplication.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/domain/Shop.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/ShopRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/AccessShopRepository.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoDatabase.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/repository/AccessShopRepositoryTest.java`

**Interfaces:**
- Produces `Optional<Shop> findShopByNormalizedName(Connection, String normalizedShopName)`.
- Produces `PageResult<ShopAdminSummary> searchShops(Connection, ShopAdminQuery)`.

- [ ] **Step 1: Write failing Access tests**

Assert statement text survives insert/update; normalized name lookup treats `Campus Shop` and ` campus shop ` as equal; rejected applications do not appear in the shop-name lookup; shop paging returns stable `shopName, shopId` ordering.

- [ ] **Step 2: Run the repository test and verify red**

Run: `mvn -pl vcampus-server -am '-Dtest=AccessShopRepositoryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: FAIL on missing columns and methods.

- [ ] **Step 3: Update schema and mappings**

Add `applicationStatement MEMO NOT NULL` to `tblSellerApplication` and `normalizedShopName VARCHAR(128) NOT NULL` to `tblShop`, with a unique index on `normalizedShopName`. Add a unique index on `tblSellerApplication(applicantUserId)` to enforce one application per user. `SaveSellerDraftCommand` component order is `applicationId, shopName, description, category, contact, applicationStatement, expectedVersion`; `SellerApplicationView` adds `applicationStatement` immediately after `contact`. Map the same order through domain, repository, services, Demo inserts, and client fixtures.

- [ ] **Step 4: Run repository and schema consumers**

Run: `mvn -pl vcampus-server -am '-Dtest=AccessShopRepositoryTest,ShopAuthDemoDatabaseTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: PASS after Demo inserts supply `applicationStatement = "Demo 经营计划"` and `normalizedShopName = shopName.strip().toLowerCase(Locale.ROOT)`.

- [ ] **Step 5: Commit**

```powershell
git add -- vcampus-database/schema/050_shop.sql vcampus-server/src/main/java/edu/seu/vcampus/server/shop/domain vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository vcampus-server/src/test/java/edu/seu/vcampus/server/shop/repository
git commit -m "feat(shop): persist application governance data"
```

### Task 3: Make administrator authorization session-scoped

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/port/ShopUserPort.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/adapter/FoundationShopUserAdapter.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/DemoShopUserPort.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/testutil/FakeShopUserPort.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/adapter/FoundationShopUserAdapterTest.java`

**Interfaces:**
- Replaces `ShopUser requireAdministrator()` with `ShopUser requireAdministrator(String sessionToken)`.

- [ ] **Step 1: Write failing adapter tests**

```java
@Test void activeAdminSessionIsAccepted() {
    ShopUser user = adapter.requireAdministrator("admin-session");
    assertThat(user.kind()).isEqualTo(ShopUserKind.ADMINISTRATOR);
}

@Test void studentSessionIsForbiddenForAdministration() {
    assertThatThrownBy(() -> adapter.requireAdministrator("student-session"))
            .isInstanceOfSatisfying(ShopAccessException.class,
                    e -> assertThat(e.code()).isEqualTo("AUTH_FORBIDDEN"));
}
```

- [ ] **Step 2: Run adapter tests and verify red**

Run: `mvn -pl vcampus-server -am '-Dtest=FoundationShopUserAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: FAIL because the adapter has a parameterless method that always rejects.

- [ ] **Step 3: Implement through existing `requireUser(sessionToken)`**

```java
default ShopUser requireAdministrator(String sessionToken) {
    ShopUser user = requireUser(sessionToken);
    if (!user.active() || user.kind() != ShopUserKind.ADMINISTRATOR)
        throw new ShopAccessException("AUTH_FORBIDDEN");
    return user;
}
```

Keep Foundation session expiry and initial-password restrictions unchanged.

- [ ] **Step 4: Run server tests and commit**

Run: `mvn -pl vcampus-server -am test`

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop vcampus-server/src/test/java/edu/seu/vcampus/server/shop
git commit -m "fix(shop): authorize administrators by session"
```

### Task 4: Complete seller application state transitions

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/SellerApplicationService.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/SellerApplicationServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ShopOwnershipTest.java`

**Interfaces:**
- Preserves `saveDraft`, `submitApplication`, and `getMyApplication` with session token.
- Adds `Optional<SellerApplicationView> findMyApplication(String sessionToken)` so “无申请” is a normal UI state.

- [ ] **Step 1: Write failing transition tests**

Cover student and teacher eligibility, administrator rejection, statement required at submission, supported category, pending read-only behavior, rejected edit clearing prior review fields, resubmission preserving application ID, one application per user, and shop-name collision at submit.

- [ ] **Step 2: Run focused tests and verify red**

Run: `mvn -pl vcampus-server -am '-Dtest=SellerApplicationServiceTest,ShopOwnershipTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: FAIL on statement, category, collision, and optional no-application behavior.

- [ ] **Step 3: Implement normalized locks and validation**

Lock `USER:<actorId>`, `SELLER_APPLICATION:<applicationId>` when present, and `SHOP_NAME:<normalizedName>` on submit. Draft save accepts DRAFT/REJECTED only; submit accepts DRAFT only and validates every required field. Convert rejected edits back to DRAFT and clear reason/reviewer/reviewedAt.

- [ ] **Step 4: Run focused and full server tests**

Run:

```powershell
mvn -pl vcampus-server -am '-Dtest=SellerApplicationServiceTest,ShopOwnershipTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl vcampus-server -am test
```

- [ ] **Step 5: Commit**

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/SellerApplicationService.java vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service
git commit -m "feat(shop): complete seller application workflow"
```

### Task 5: Complete administrator review and shop status services

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ShopAdminService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/logging/ShopBusinessLogger.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ShopStatusServiceTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/logging/ShopBusinessLoggerTest.java`

**Interfaces:**
- Changes admin methods to accept `String sessionToken` first.
- Produces `PageResult<ShopAdminSummary> searchShops(String sessionToken, ShopAdminQuery query)`.

- [ ] **Step 1: Write failing admin service tests**

Assert only an active admin session can search/review/suspend/resume; approval rechecks version, eligibility, owner count, and normalized name; rejection requires reason; suspension requires reason; every state mutation emits actor ID, target ID, old/new status, and reason to the Shop logger seam.

- [ ] **Step 2: Run focused tests and verify red**

Run: `mvn -pl vcampus-server -am '-Dtest=ShopStatusServiceTest,ShopBusinessLoggerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: FAIL because methods use parameterless admin authorization and do not expose search/log seams.

- [ ] **Step 3: Implement session-aware methods and atomic approval**

Approval locks application, user, and normalized shop name, then re-reads all records in one transaction. Create ACTIVE shop and mark APPROVED in that transaction. Suspension stores current reason/actor/time; resume clears suspension fields so active views do not carry stale suspension state.

- [ ] **Step 4: Run server tests and commit**

Run: `mvn -pl vcampus-server -am test`

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ShopAdminService.java vcampus-server/src/main/java/edu/seu/vcampus/server/shop/logging/ShopBusinessLogger.java vcampus-server/src/test/java/edu/seu/vcampus/server/shop
git commit -m "feat(shop): govern applications and shop status"
```

### Task 6: Expose seller application and admin approval over Socket

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/ShopHandlerSupport.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/SellerShopHandlers.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/AdminShopHandlers.java`
- Refactor within Shop: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/BuyerShopHandlers.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler/SellerShopHandlersTest.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler/AdminShopHandlersTest.java`

**Interfaces:**
- Seller commands: `SHOP_SELLER_GET_APPLICATION`, `SHOP_SELLER_SAVE_APPLICATION`, `SHOP_SELLER_SUBMIT_APPLICATION`.
- Admin commands: `SHOP_ADMIN_SEARCH_APPLICATIONS`, `SHOP_ADMIN_REVIEW_APPLICATION`, `SHOP_ADMIN_SEARCH_SHOPS`, `SHOP_ADMIN_SUSPEND_SHOP`, `SHOP_ADMIN_RESUME_SHOP`.

- [ ] **Step 1: Write failing handler tests**

For each command assert body type, session propagation, success DTO, stable Shop error code, `AUTH_FORBIDDEN`, deduplication on writes, and command-completed logging. Send an applicant ID in a forged body type and assert validation failure rather than cross-user access.

- [ ] **Step 2: Run focused tests and verify red**

Run: `mvn -pl vcampus-server -am '-Dtest=SellerShopHandlersTest,AdminShopHandlersTest,BuyerShopHandlersTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: FAIL because new handlers and commands are unregistered.

- [ ] **Step 3: Extract Shop-only handler support and register operations**

`ShopHandlerSupport` owns body casting, `ResponseBody` mapping, elapsed logging, and `RequestDeduplicator`; it receives existing Router types as dependencies without changing them. Read handlers still authenticate before invoking services; write handlers deduplicate by actor and connection.

- [ ] **Step 4: Run handler and server suites**

Run:

```powershell
mvn -pl vcampus-server -am '-Dtest=SellerShopHandlersTest,AdminShopHandlersTest,BuyerShopHandlersTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl vcampus-server -am test
```

- [ ] **Step 5: Commit**

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler
git commit -m "feat(shop): expose seller application administration"
```

### Task 7: Add seller/admin client capability ports

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/SellerShopClientPort.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/AdminShopClientPort.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientService.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/service/ShopClientServiceTest.java`

**Interfaces:**
- `SellerShopClientPort`: `getMyApplication`, `saveApplication`, `submitApplication`.
- `AdminShopClientPort`: `searchApplications`, `reviewApplication`, `searchShops`, `suspendShop`, `resumeShop`.

- [ ] **Step 1: Write failing command-mapping tests**

Assert every method sends the exact command and body, uses the configured timeout, maps failure codes to `ShopClientException`, and returns typed `CompletableFuture` data.

- [ ] **Step 2: Run test and verify red**

Run: `mvn -pl vcampus-client -am '-Dtest=ShopClientServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

- [ ] **Step 3: Implement the two interfaces on `ShopClientService`**

Reuse the existing private generic `send(String, Serializable)` method. Return an explicit serializable empty response for suspend/resume rather than accepting null response data.

- [ ] **Step 4: Run client service tests and commit**

Run: `mvn -pl vcampus-client -am '-Dtest=ShopClientServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service vcampus-client/src/test/java/edu/seu/vcampus/client/shop/service
git commit -m "feat(shop): add seller and admin client ports"
```

### Task 8: Build application, approval, and shop-status Swing pages

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopRoute.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/MyShopPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SellerApplicationPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ShopAdminPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ApplicationReviewPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ShopStatusPanel.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/SellerApplicationPanelTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/admin/ApplicationReviewPanelTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`

**Interfaces:**
- Adds routes `SellerApplication`, `SellerWorkspace`, and `AdminWorkspace`.
- `MyShopPanel` receives role, navigator, buyer port, seller port, and admin port, then derives the action label from application/owned-shop state.

- [ ] **Step 1: Write failing My/application UI tests**

Assert student/teacher actions for none, draft, pending, rejected, and approved; admin sees `商城管理`; pending fields are disabled; rejected reason is visible; resubmit keeps the application ID; all pages return through `ShopNavigator`.

- [ ] **Step 2: Write failing admin UI tests**

Assert status filtering and table selection, full material details, approve confirmation callback, required reject reason, stale-version reload, required suspension reason, and resume action.

- [ ] **Step 3: Run focused client tests and verify red**

Run: `mvn -pl vcampus-client -am '-Dtest=SellerApplicationPanelTest,ApplicationReviewPanelTest,ShopUiTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

- [ ] **Step 4: Implement focused panels and coordinator routing**

Use `LatestRequest` per page. Keep form construction, state rendering, and submission methods separate. `ShopPageCoordinator` registers stable card IDs and passes only required capability ports. All async completion mutates Swing state through `SwingUtilities.invokeLater`.

- [ ] **Step 5: Run client suite and commit**

Run: `mvn -pl vcampus-client -am test`

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop vcampus-client/src/test/java/edu/seu/vcampus/client/shop
git commit -m "feat(shop): add application and approval ui"
```

### Task 9: Application checkpoint

- [ ] **Step 1: Run all Shop suites and inspect scope**

```powershell
mvn -pl vcampus-common test
mvn -pl vcampus-server -am test
mvn -pl vcampus-client -am test
git diff --check
git status --short
```

Expected: all tests PASS; `logs/` remains untracked; no shared framework implementation appears in `origin/SHOP..HEAD`.
