# vCampus Shop Usability Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Subagents and parallel agents are prohibited for this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair all twelve confirmed Shop manual-test issues, standardize the final Demo on port 8888, and make the Shop module compatible with `origin/feat/user-management@8d53d9e` without modifying User Manager internals.

**Architecture:** Keep the existing Common/Server/Client Shop layers and add narrow seams for User Manager shell installation, guarded navigation, scenario-configured product cards, responsive purchase views, preset covers, and master-detail management. Preserve the existing database schema and authoritative server calculations; Swing components render view models and emit callbacks so the final UI remains replaceable.

**Tech Stack:** Java 21, Swing, Maven multi-module build, JUnit 5, AssertJ, Mockito, UCanAccess/Microsoft Access, PowerShell release scripts.

**Spec:** `docs/superpowers/specs/2026-09-01-vcampus-shop-usability-remediation-design.md`

## Global Constraints

- Work only in `E:\summer-school\vCampus\.worktrees\shop-auth-demo` on branch `SHOP`.
- Before every execution batch report worktree, branch, HEAD, uncommitted files, and `origin/SHOP` tracking state.
- Preserve untracked `logs/`; do not delete, clean, stage, or commit it.
- Do not use subagents or parallel agents.
- Modify only `common/shop`, `server/shop`, `client/shop`, Shop database/Demo/tests, release scripts, and Shop documentation.
- Foundation, User, Socket, Router, transaction framework, and public networking implementations are dependency-only.
- Do not push, merge, rebase, delete, roll back, or clean files without explicit fresh authorization.
- `origin/feat/user-management@8d53d9e` is the compatibility target. Current `SHOP` contains User Manager only through `faded03`; this plan does not authorize merging the newer branch.
- Do not add or alter database tables. Reuse `coverImageUrl` for controlled `builtin://shop/...` identifiers.
- Follow strict TDD: add one focused failing test, run it and confirm the expected failure, implement the minimum behavior, then rerun the focused test.
- Keep business state, navigation, loading, and Swing rendering separable.
- Make local commits only; never include `.superpowers/brainstorm/` or `logs/`.

## File Structure

New focused types introduced by this plan:

- `vcampus-common/.../shop/ShopCoverPreset.java`: one safe preset projection.
- `vcampus-common/.../shop/ShopCoverPresets.java`: the canonical 20-preset allowlist.
- `vcampus-common/.../shop/SellerApplicationListMode.java`: pending/processed query mode.
- `vcampus-client/.../shop/ui/navigation/ShopLeaveGuard.java`: asynchronous leave permission seam.
- `vcampus-client/.../shop/ui/buyer/ProductCardContext.java`: home/search/storefront visibility rules.
- `vcampus-client/.../shop/ui/catalog/BuiltinProductImageLoader.java`: built-in rendering plus legacy HTTPS fallback.
- `vcampus-client/.../shop/ui/seller/CoverPresetPickerPanel.java`: category-aware cover selection.
- `vcampus-client/.../shop/ui/seller/SkuEditorDialog.java`: business-field-only SKU editor.
- `vcampus-client/.../shop/ui/buyer/CartItemCard.java`: one responsive cart card.
- `vcampus-client/.../shop/ui/buyer/CheckoutItemRow.java`: one read-only checkout row.

Existing large coordinators remain in place; this plan extracts only behavior required by the confirmed issues.

---

### Task 1: User Manager Compatibility Boundary and Port 8888

**Files:**
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoServerMain.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMain.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/MyShopPanel.java`
- Modify: `vcampus-distribution/scripts/start-shop-auth-demo-server.ps1`
- Modify: `vcampus-distribution/scripts/start-shop-auth-demo-client.ps1`
- Modify: `vcampus-distribution/templates/shop-auth-demo/启动服务端.bat`
- Modify: `vcampus-distribution/templates/shop-auth-demo/启动客户端.bat`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoServerMainTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMainTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/MyShopRoleActionTest.java`
- Test: `vcampus-distribution/scripts/tests/start-shop-auth-demo-scripts.tests.ps1`

**Interfaces:**
- Consumes: `MainFrame`, `LoginResult.user()`, `UserView`, shared `ClientConnection`, and existing `ShopUiInstaller.install(...)`.
- Produces: default server address `127.0.0.1:8888`; a Shop “我的” page containing Shop business only; unchanged explicit host/port override.

- [ ] **Step 1: Change tests to require port 8888 and remove duplicated account content**

```java
@Test
void defaultsToTheUnifiedLocalServerPort() {
    var address = ShopAuthDemoClientMain.serverAddress(new String[0]);
    assertThat(address).isEqualTo(
            new ShopAuthDemoClientMain.ServerAddress("127.0.0.1", 8888));
}

@Test
void myPageContainsShopBusinessButNotUserManagerAccountFields() throws Exception {
    SellerShopClientPort seller = mock(SellerShopClientPort.class);
    when(seller.getMyApplication()).thenReturn(completedFuture(Optional.empty()));
    Fixture fixture = fixture(UserRole.STUDENT, seller);
    assertThat(findNamed(fixture.panel(), "my.user-id")).isNull();
    assertThat(findNamed(fixture.panel(), "my.login-id")).isNull();
    assertThat(findNamed(fixture.panel(), "my.account-status")).isNull();
    assertThat(ShopSwingTestSupport.component(
            fixture.panel(), "my.business.action", JButton.class)).isNotNull();
}

private static Component findNamed(Container root, String name) {
    if (name.equals(root.getName())) return root;
    for (Component child : root.getComponents()) {
        if (name.equals(child.getName())) return child;
        if (child instanceof Container nested) {
            Component match = findNamed(nested, name);
            if (match != null) return match;
        }
    }
    return null;
}
```

Update the PowerShell script assertions from `19090` to `8888` while retaining the explicit remote `23456` case.

- [ ] **Step 2: Run the focused tests and observe the expected red state**

Run:

```powershell
mvn -pl vcampus-client,vcampus-server -am '-Dtest=ShopAuthDemoClientMainTest,MyShopRoleActionTest,ShopAuthDemoServerMainTest' test
& .\vcampus-distribution\scripts\tests\start-shop-auth-demo-scripts.tests.ps1
```

Expected: Java assertions report `19090` instead of `8888`; the My page still contains account identity labels; script assertions find `19090`.

- [ ] **Step 3: Apply the minimal compatibility implementation**

Set both Demo defaults to one constant value:

```java
private static final int DEFAULT_PORT = 8888;
```

Remove `my.user-id`, `my.login-id`, `my.account-status`, and other User Manager-owned account rows from `MyShopPanel`; keep order history, application, seller workspace, and admin workspace actions. Continue passing the shared `UserView` only for Shop role selection.

Change every Shop Demo launch argument in the four startup scripts from `19090` to `8888`. Do not change the general dynamic-port test infrastructure.

- [ ] **Step 4: Run focused tests to green**

Run the commands from Step 2.

Expected: all selected Maven tests and the startup script test pass; explicit host/port override remains `23456`.

- [ ] **Step 5: Verify the latest User Manager public seam without merging it**

Run:

```powershell
git show origin/feat/user-management:vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java | Select-String 'pageNavigator|content'
git show origin/feat/user-management:vcampus-client/src/main/java/edu/seu/vcampus/client/user/ui/UserUiCoordinator.java | Select-String 'MainFrame'
mvn -pl vcampus-client -am '-Dtest=ShopUiTest,ShopAuthDemoClientMainTest,MyShopRoleActionTest' test
```

Expected: the target `MainFrame` retains the public content/navigation seams used by `ShopUiInstaller`; Shop tests pass against the currently merged compatible shell. Do not claim full joint compilation with `8d53d9e` until that commit is present in an explicitly authorized integration baseline.

- [ ] **Step 6: Commit**

```powershell
git add -- vcampus-server/src/main/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoServerMain.java vcampus-server/src/test/java/edu/seu/vcampus/server/shop/demo/ShopAuthDemoServerMainTest.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMain.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/MyShopPanel.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMainTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/MyShopRoleActionTest.java vcampus-distribution/scripts/start-shop-auth-demo-server.ps1 vcampus-distribution/scripts/start-shop-auth-demo-client.ps1 vcampus-distribution/templates/shop-auth-demo/启动服务端.bat vcampus-distribution/templates/shop-auth-demo/启动客户端.bat vcampus-distribution/scripts/tests/start-shop-auth-demo-scripts.tests.ps1
git commit -m "fix(shop-demo): align user shell and port"
```

### Task 2: Utility-Page Navigation and Leave Guard

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopLeaveGuard.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigator.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopToolbar.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigatorTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`

**Interfaces:**
- Consumes: existing `ShopRoute` records and `ShopRouteHost.capture/render`.
- Produces: `ShopLeaveGuard.requestLeave(Runnable proceed)`; `ShopNavigator.setLeaveGuard(...)`; utility anchor semantics for `My` and `Cart`.

- [ ] **Step 1: Add the six confirmed navigation scenarios as failing tests**

```java
@ParameterizedTest
@MethodSource("utilitySequences")
void utilityPagesReturnOnceToTheirContentAnchor(List<ShopRoute> sequence) {
    ShopRoute home = new ShopRoute.Home(new HomeProductQuery(
            null, null, ProductSortMode.SALES_DESC, 0, 20));
    ShopNavigator navigator = new ShopNavigator(route -> { });
    navigator.open(home);
    sequence.forEach(navigator::open);

    navigator.back();

    assertThat(navigator.current()).contains(home);
    assertThat(navigator.history()).isEmpty();
}

static Stream<List<ShopRoute>> utilitySequences() {
    return Stream.of(
            List.of(new ShopRoute.My()),
            List.of(new ShopRoute.Cart()),
            List.of(new ShopRoute.My(), new ShopRoute.Cart()),
            List.of(new ShopRoute.Cart(), new ShopRoute.My()),
            List.of(new ShopRoute.My(), new ShopRoute.Cart(), new ShopRoute.My()),
            List.of(new ShopRoute.Cart(), new ShopRoute.My(), new ShopRoute.My()));
}
```

Add this guard test:

```java
@Test
void leaveGuardCommitsNoNavigationStateUntilProceedRuns() {
    AtomicReference<Runnable> held = new AtomicReference<>();
    ShopRoute home = new ShopRoute.Home(new HomeProductQuery(
            null, null, ProductSortMode.SALES_DESC, 0, 20));
    ShopRoute product = new ShopRoute.Product("p1");
    ShopNavigator navigator = new ShopNavigator(route -> { });
    navigator.open(home);
    navigator.setLeaveGuard(held::set);

    navigator.open(product);
    assertThat(navigator.current()).contains(home);
    assertThat(navigator.history()).isEmpty();

    held.get().run();
    assertThat(navigator.current()).contains(product);
    assertThat(navigator.history()).containsExactly(home);
}
```

- [ ] **Step 2: Run the navigator test to verify it fails for the right reason**

Run:

```powershell
mvn -pl vcampus-client -am -Dtest=ShopNavigatorTest test
```

Expected: My/Cart accumulate history, and no leave-guard API exists.

- [ ] **Step 3: Add the guard and utility-anchor state machine**

```java
@FunctionalInterface
public interface ShopLeaveGuard {
    void requestLeave(Runnable proceed);

    static ShopLeaveGuard immediate() { return Runnable::run; }
}
```

In `ShopNavigator`, keep `ShopRoute utilityAnchor` and a current `ShopLeaveGuard`. Route every `open` and `back` transition through the guard. The transition body must:

```java
if (isUtility(current) && isUtility(target)) {
    current = target;              // replace, do not push
} else if (!isUtility(current) && isUtility(target)) {
    utilityAnchor = host.capture(current);
    current = target;
} else {
    utilityAnchor = null;
    addHistory(host.capture(current));
    current = target;
}
```

When backing from a utility page, restore `utilityAnchor` directly and clear it. Repeated current-route clicks only republish captured state and never grow history.

- [ ] **Step 4: Wire all toolbar navigation through the same navigator**

`ShopToolbar` continues to call `navigator.open(new ShopRoute.My())` and `navigator.open(new ShopRoute.Cart())`; delete any toolbar-local history behavior. `ShopPageCoordinator` sets the active page guard during render and resets it to `ShopLeaveGuard.immediate()` for pages without unsaved state.

- [ ] **Step 5: Run focused navigation and UI tests**

```powershell
mvn -pl vcampus-client -am '-Dtest=ShopNavigatorTest,ShopUiTest' test
```

Expected: all six utility sequences, repeated-click behavior, normal content history, checkout completion, and toolbar navigation pass.

- [ ] **Step 6: Commit**

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopLeaveGuard.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigator.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopToolbar.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigatorTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java
git commit -m "fix(shop): anchor utility navigation"
```

### Task 3: Direct Seller-Application Submission and Unsaved-Change Protection

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SellerApplicationPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/SellerApplicationPanelTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigatorTest.java`

**Interfaces:**
- Consumes: `SellerShopClientPort.saveApplication(...)`, `submitApplication(...)`, and Task 2 `ShopLeaveGuard`.
- Produces: `SellerApplicationPanel.requestLeave(Runnable proceed)` and sequential save-then-submit behavior.

- [ ] **Step 1: Add direct-submit and three-choice leave tests**

```java
@Test
void completeNewApplicationSavesLatestFieldsThenSubmitsReturnedVersion() {
    when(port.saveApplication(any())).thenReturn(completedFuture(savedDraft("a-1", 3)));
    when(port.submitApplication(new SubmitSellerApplicationCommand("a-1", 3)))
            .thenReturn(completedFuture(pending("a-1", 4)));

    onEdt(() -> fillValidForm(panel));
    onEdt(() -> component(panel, "seller.application.submit", JButton.class).doClick());
    flushEdt();

    InOrder order = inOrder(port);
    order.verify(port).saveApplication(argThat(command -> command.applicationId() == null));
    order.verify(port).submitApplication(new SubmitSellerApplicationCommand("a-1", 3));
}
```

Add these concrete private fixtures to the test class:

```java
private static void fillValidForm(SellerApplicationPanel panel) {
    component(panel, "seller.application.name", JTextField.class).setText("东南校园店");
    component(panel, "seller.application.description", JTextArea.class).setText("校园用品");
    component(panel, "seller.application.contact", JTextField.class).setText("13800000000");
    component(panel, "seller.application.statement", JTextArea.class).setText("稳定经营");
}

private static SellerApplicationView savedDraft(String id, long version) {
    return new SellerApplicationView(id, "student-1", "东南校园店", "校园用品", "文具",
            "13800000000", "稳定经营", SellerApplicationStatus.DRAFT,
            null, null, null, null, version);
}

private static SellerApplicationView pending(String id, long version) {
    SellerApplicationView draft = savedDraft(id, version);
    return new SellerApplicationView(id, draft.applicantUserId(), draft.shopName(),
            draft.description(), draft.category(), draft.contact(), draft.applicationStatement(),
            SellerApplicationStatus.PENDING, null, null, Instant.EPOCH, null, version);
}
```

Add these exact cases using an injectable recording leave prompt:

| Test method | Setup | Required assertion |
| --- | --- | --- |
| `saveFailurePreventsSubmitAndKeepsForm` | `saveApplication` returns failed future | `verify(port, never()).submitApplication(any())`; all five form values unchanged |
| `submitFailureKeepsReturnedDraftAndLatestSnapshot` | save returns version 3, submit fails | a subsequent `requestLeave` invokes `proceed` without showing the prompt |
| `unchangedSavedFormLeavesWithoutPrompt` | load a draft and make no edit | prompt invocation count 0; proceed count 1 |
| `dirtySaveChoiceWaitsForSaveBeforeProceeding` | edit name; keep save future incomplete | proceed count 0 before completion and 1 after successful completion |
| `dirtyDiscardChoiceProceedsWithoutSaving` | edit name; choose discard | `verify(port, never()).saveApplication(any())`; proceed count 1 |
| `dirtyCancelChoiceDoesNotProceed` | edit name; choose cancel | proceed count 0; edited name remains visible |

- [ ] **Step 2: Run the focused test and confirm the red cause**

```powershell
mvn -pl vcampus-client -am -Dtest=SellerApplicationPanelTest test
```

Expected: submit is disabled for a new form, does not save first, and the panel has no leave-guard behavior.

- [ ] **Step 3: Capture normalized form snapshots and dirty state**

Create a private immutable form snapshot in `SellerApplicationPanel`:

```java
private record FormState(String shopName, String description, String category,
        String contact, String statement) {
    SaveSellerDraftCommand toCommand(SellerApplicationView current) {
        return new SaveSellerDraftCommand(current == null ? null : current.applicationId(),
                shopName, description, category, contact, statement,
                current == null ? 0 : current.rowVersion());
    }
}
```

Store `savedSnapshot` after load/save/submit. `dirty()` compares it with `captureForm()`. Do not use focus state.

- [ ] **Step 4: Implement save-then-submit with one in-flight gate**

```java
private void submitLatest() {
    FormState form = requireValid(captureForm());
    setSubmitting(true);
    port.saveApplication(form.toCommand(current)).whenComplete((saved, saveFailure) ->
            onEdt(() -> {
                if (saveFailure != null) {
                    finishMutation(null, saveFailure);
                    return;
                }
                current = saved;
                savedSnapshot = form;
                port.submitApplication(new SubmitSellerApplicationCommand(
                                saved.applicationId(), saved.rowVersion()))
                        .whenComplete(this::finishMutation);
            }));
}
```

Keep the saved draft when the second stage fails. Restore buttons on the EDT. Validation errors leave the form untouched.

- [ ] **Step 5: Implement the leave choice and coordinator wiring**

Use one three-option `JOptionPane` in the production constructor and an injectable prompt seam in tests. `requestLeave(proceed)` immediately proceeds when clean; otherwise it maps “保存并离开”, “不保存并离开”, and “取消” exactly. Saving must wait for a successful future before calling `proceed`.

`ShopPageCoordinator` installs `application::requestLeave` only while `ShopRoute.SellerApplication` is active.

- [ ] **Step 6: Run focused tests**

```powershell
mvn -pl vcampus-client -am '-Dtest=SellerApplicationPanelTest,ShopNavigatorTest,ShopUiTest' test
```

Expected: direct new/rejected submission, failure preservation, and every guarded exit pass.

- [ ] **Step 7: Commit**

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SellerApplicationPanel.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/SellerApplicationPanelTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigatorTest.java
git commit -m "fix(shop): protect seller application edits"
```

### Task 4: Built-In Cover Contract and Validation

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopCoverPreset.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopCoverPresets.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ProductImageUrl.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ProductService.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/AdminProductService.java`
- Create: `vcampus-common/src/test/java/edu/seu/vcampus/common/shop/ShopCoverPresetsTest.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ProductServiceTest.java`
- Modify: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/AdminProductServiceTest.java`

**Interfaces:**
- Consumes: `ShopCategories`, existing `coverImageUrl` command fields, and `SHOP_COVER_IMAGE_URL_INVALID`.
- Produces: immutable 20-item allowlist and `ProductImageUrl.validate(String raw, String category)`.

- [ ] **Step 1: Write failing Common contract tests**

```java
@Test
void exposesFourUniqueBuiltinCoversForEveryShopCategory() {
    assertThat(ShopCoverPresets.all()).hasSize(20)
            .extracting(ShopCoverPreset::id).doesNotHaveDuplicates();
    for (String category : ShopCategories.ALL) {
        assertThat(ShopCoverPresets.forCategory(category)).hasSize(4)
                .allMatch(preset -> preset.category().equals(category))
                .allMatch(preset -> preset.id().startsWith("builtin://shop/"));
    }
}
```

Add server tests accepting a matching built-in ID, rejecting a different-category ID, preserving `null`, and continuing to accept a legacy credential-free HTTPS URL.

- [ ] **Step 2: Run tests to red**

```powershell
mvn -pl vcampus-common -Dtest=ShopCoverPresetsTest test
mvn -pl vcampus-server -am '-Dtest=ProductServiceTest,AdminProductServiceTest' test
```

Expected: preset types do not exist and `ProductImageUrl` rejects the `builtin` scheme.

- [ ] **Step 3: Implement the canonical preset catalog**

```java
public record ShopCoverPreset(String id, String category, String displayName)
        implements Serializable { }

public final class ShopCoverPresets {
    private static final List<ShopCoverPreset> ALL = List.of(
            preset("stationery/writing-1", "文具", "书写工具"),
            preset("stationery/notebook-1", "文具", "笔记用品"),
            preset("stationery/ruler-1", "文具", "测量工具"),
            preset("stationery/marker-1", "文具", "标记用品"),
            preset("books/textbook-1", "图书", "教材"),
            preset("books/reading-1", "图书", "课外阅读"),
            preset("books/reference-1", "图书", "工具书"),
            preset("books/literature-1", "图书", "文学"),
            preset("daily/cleaning-1", "生活用品", "清洁用品"),
            preset("daily/storage-1", "生活用品", "收纳用品"),
            preset("daily/drinkware-1", "生活用品", "饮水用品"),
            preset("daily/care-1", "生活用品", "日常护理"),
            preset("medicine/first-aid-1", "药品", "应急护理"),
            preset("medicine/cold-care-1", "药品", "感冒护理"),
            preset("medicine/pain-care-1", "药品", "疼痛护理"),
            preset("medicine/health-1", "药品", "健康用品"),
            preset("other/digital-1", "其他", "数码用品"),
            preset("other/sports-1", "其他", "运动用品"),
            preset("other/gift-1", "其他", "礼品"),
            preset("other/general-1", "其他", "通用商品"));
}
```

Return unmodifiable lists and optional lookup results. Validate categories with `ShopCategories`.

- [ ] **Step 4: Extend server validation without a schema change**

```java
static String validate(String raw, String category) {
    if (raw == null || raw.isBlank()) return null;
    String value = raw.strip();
    if (value.startsWith("builtin://")) {
        ShopCoverPreset preset = ShopCoverPresets.find(value).orElseThrow(ProductImageUrl::invalid);
        if (!preset.category().equals(ShopCategories.requireSupported(category))) throw invalid();
        return preset.id();
    }
    return validateLegacyHttps(value);
}
```

Update seller and admin create/update services to pass the command category. Do not change database DDL or repository columns.

- [ ] **Step 5: Run focused Common and server tests**

```powershell
mvn -pl vcampus-common -Dtest=ShopCoverPresetsTest,CatalogContractTest test
mvn -pl vcampus-server -am '-Dtest=ProductServiceTest,AdminProductServiceTest' test
```

Expected: all preset, legacy, mismatch, and null cases pass.

- [ ] **Step 6: Commit**

```powershell
git add -- vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopCoverPreset.java vcampus-common/src/main/java/edu/seu/vcampus/common/shop/ShopCoverPresets.java vcampus-common/src/test/java/edu/seu/vcampus/common/shop/ShopCoverPresetsTest.java vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ProductImageUrl.java vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/ProductService.java vcampus-server/src/main/java/edu/seu/vcampus/server/shop/service/AdminProductService.java vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/ProductServiceTest.java vcampus-server/src/test/java/edu/seu/vcampus/server/shop/service/AdminProductServiceTest.java
git commit -m "feat(shop): define built-in product covers"
```

### Task 5: Seller Master-Detail Workspace, Cover Picker, and Business-Only SKU Editing

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/BuiltinProductImageLoader.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/CoverPresetPickerPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SkuEditorDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ProductEditorPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ProductManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/AdminProductManagementPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/ProductManagementPanelTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/admin/AdminProductManagementPanelTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/CoverPresetPickerPanelTest.java`

**Interfaces:**
- Consumes: Task 4 `ShopCoverPresets`, `CreateSkuCommand`, and `UpsertSkuCommand`.
- Produces: category-aware `selectedCoverId()`; read-only SKU summary; dialog output containing only business-editable fields.

- [ ] **Step 1: Write failing UI contract tests**

```java
@Test
void sellerNeverEditsSkuIdVersionOrRawCoverUrl() {
    ProductEditorPanel editor = onEdt(() -> new ProductEditorPanel(uiKit));
    assertThat(findNamed(editor, "seller.editor.cover")).isNull();
    assertThat(findNamed(editor, "seller.editor.sku.id")).isNull();
    assertThat(findNamed(editor, "seller.editor.sku.version")).isNull();
    JTable table = component(editor, "seller.editor.skus", JTable.class);
    assertThat(List.of(columnNames(table)))
            .containsExactly("规格名称", "单价", "库存", "状态", "操作");
    assertThat(table.getModel().isCellEditable(0, 0)).isFalse();
}
```

Use the same recursive `findNamed(Container, String)` helper shown in Task 1 inside this test class; keep it test-local rather than adding production lookup APIs.

Add these exact tests:

| Test method | Required assertion |
| --- | --- |
| `pickerShowsExactlyFourMatchingCovers(String category)` | for each of the five categories, button property `shop.cover.id` exactly matches `ShopCoverPresets.forCategory(category)` |
| `changingCategoryClearsAnIncompatibleSelection` | selecting a stationery cover and switching to books makes `selectedCoverId()` return `null` |
| `addSkuMapsOnlyNamePriceStockAndActiveToCreateCommand` | full equality with `new CreateSkuCommand("蓝色", new BigDecimal("3.50"), 20, true)` |
| `editSkuRetainsHiddenIdAndVersionInUpdateCommand` | full equality with `new UpsertSkuCommand("sku-7", "蓝色", new BigDecimal("3.50"), 20, true, 4)` |

- [ ] **Step 2: Run seller/admin tests to red**

```powershell
mvn -pl vcampus-client -am '-Dtest=ProductManagementPanelTest,AdminProductManagementPanelTest,CoverPresetPickerPanelTest' test
```

Expected: raw URL and technical columns are present; picker/dialog classes do not exist; existing workspace layout assertions fail.

- [ ] **Step 3: Implement deterministic built-in thumbnails**

`BuiltinProductImageLoader` recognizes `builtin://shop/...`, creates a fixed `BufferedImage` using a category color, simple symbol, and preset display name, and returns it asynchronously. For `null`, return the default placeholder. Delegate legacy HTTPS values to `HttpsProductImageLoader`.

```java
if (coverId == null) return completedFuture(defaultPlaceholder(size));
return ShopCoverPresets.find(coverId)
        .map(preset -> completedFuture(renderPreset(preset, size)))
        .orElseGet(() -> https.load(coverId, category, size));
```

Keep drawing deterministic and offline; no image database or network is introduced for new edits.

- [ ] **Step 4: Replace raw URL entry with the picker**

`CoverPresetPickerPanel.setCategory(category)` renders four selectable thumbnails from `ShopCoverPresets.forCategory(category)`. `selectedCoverId()` returns the stable ID or `null`. When the category changes and the selected preset no longer belongs, clear selection and show the default placeholder.

- [ ] **Step 5: Replace editable SKU cells with dialog actions**

`SkuEditorDialog.Result` contains only:

```java
record Result(String name, BigDecimal unitPrice, long stockQuantity, boolean active) { }
```

`ProductEditorPanel` privately stores an `EditableSku` with optional ID/version. Mapping rules are:

```java
CreateSkuCommand create = new CreateSkuCommand(
        result.name(), result.unitPrice(), result.stockQuantity(), result.active());
UpsertSkuCommand update = new UpsertSkuCommand(
        existing.skuId(), result.name(), result.unitPrice(),
        result.stockQuantity(), result.active(), existing.rowVersion());
```

The table stays read-only and exposes Add/Edit actions only.

- [ ] **Step 6: Build the selected layout A**

Use one horizontal `JSplitPane`: product summary table on the left and `ProductEditorPanel` on the right. Selecting a row loads its details; “新建商品” clears the editor. Admin management reuses the same editor and adds only the shop selector and admin actions in its outer panel.

- [ ] **Step 7: Run focused UI tests**

```powershell
mvn -pl vcampus-client -am '-Dtest=ProductManagementPanelTest,AdminProductManagementPanelTest,CoverPresetPickerPanelTest,SellerWorkspacePanelTest' test
```

Expected: layout A, hidden technical fields, command mappings, cover selection, and admin reuse pass.

- [ ] **Step 8: Commit**

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/BuiltinProductImageLoader.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/CoverPresetPickerPanel.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SkuEditorDialog.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ProductEditorPanel.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ProductManagementPanel.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/AdminProductManagementPanel.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/ProductManagementPanelTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/admin/AdminProductManagementPanelTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/CoverPresetPickerPanelTest.java
git commit -m "feat(shop): simplify product and sku editing"
```

### Task 6: Pending and Processed Application Review Lists

**Files:**
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerApplicationListMode.java`
- Modify: `vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerApplicationQuery.java`
- Modify: `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/AccessShopRepository.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ApplicationReviewPanel.java`
- Test: `vcampus-common/src/test/java/edu/seu/vcampus/common/shop/SellerApplicationContractTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/repository/AccessShopRepositoryTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler/AdminShopHandlersTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/service/ShopClientServiceTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/admin/ApplicationReviewPanelTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthEndToEndTest.java`

**Interfaces:**
- Consumes: existing application timestamps and paging.
- Produces: `SellerApplicationListMode.PENDING/PROCESSED`; server-side grouped, stable latest-first result pages.

- [ ] **Step 1: Write failing contract, repository, and UI tests**

```java
@Test
void processedApplicationsAreNewestReviewedFirstWithStableIdTieBreak() {
    PageResult<SellerApplication> result = repository.searchApplications(connection,
            new SellerApplicationQuery(null, SellerApplicationListMode.PROCESSED, 0, 20));
    assertThat(result.items()).extracting(SellerApplication::applicationId)
            .containsExactly("reviewed-latest-b", "reviewed-latest-a", "reviewed-old");
}
```

Add the pending repository and tab tests explicitly:

```java
@Test
void pendingApplicationsAreNewestSubmittedFirstWithStableIdTieBreak() {
    PageResult<SellerApplication> result = repository.searchApplications(connection,
            new SellerApplicationQuery(null, SellerApplicationListMode.PENDING, 0, 20));
    assertThat(result.items()).extracting(SellerApplication::applicationId)
            .containsExactly("pending-latest-a", "pending-latest-b", "pending-old");
}

@Test
void reviewPanelDefaultsToPendingAndSeparatesProcessedRows() {
    JTabbedPane tabs = component(panel, "admin.applications.tabs", JTabbedPane.class);
    assertThat(tabs.getSelectedIndex()).isZero();
    assertThat(component(panel, "admin.applications.pending", JTable.class).getRowCount()).isOne();
    assertThat(component(panel, "admin.applications.processed", JTable.class).getRowCount()).isEqualTo(2);
}
```

- [ ] **Step 2: Run tests to red**

```powershell
mvn -pl vcampus-common -Dtest=SellerApplicationContractTest test
mvn -pl vcampus-server -am -Dtest=AccessShopRepositoryTest test
mvn -pl vcampus-client -am -Dtest=ApplicationReviewPanelTest test
```

Expected: list-mode type and two-tab UI do not exist; repository uses application ID ordering.

- [ ] **Step 3: Extend the Shop query contract**

```java
public enum SellerApplicationListMode { PENDING, PROCESSED }

public record SellerApplicationQuery(String applicantUserId,
        SellerApplicationListMode mode, int pageNumber, int pageSize)
        implements Serializable { }
```

Update `ApplicationReviewPanel`, `AdminShopHandlersTest`, `ShopClientServiceTest`, and `ShopAuthEndToEndTest` constructor calls and serialization assertions. Do not change public networking machinery.

- [ ] **Step 4: Implement server grouping and order**

For `PENDING`, append:

```sql
AND applicationStatus = 'PENDING'
ORDER BY submittedAt DESC, applicationId
```

For `PROCESSED`, append:

```sql
AND applicationStatus IN ('APPROVED', 'REJECTED')
ORDER BY reviewedAt DESC, applicationId
```

Apply paging after these predicates and use the same predicate for count and item queries.

- [ ] **Step 5: Render two independent paged tabs**

Build one `JTabbedPane` with separate table models, request generations, page numbers, loading/error states, and retry actions. After successful review, reload both lists so the row moves immediately.

- [ ] **Step 6: Run focused tests**

```powershell
mvn -pl vcampus-common -Dtest=SellerApplicationContractTest test
mvn -pl vcampus-server -am '-Dtest=AccessShopRepositoryTest,ShopAdminServiceTest,AdminShopHandlersTest' test
mvn -pl vcampus-client -am '-Dtest=ApplicationReviewPanelTest,ShopUiTest' test
```

Expected: grouping, latest-first order, paging, tab defaults, and post-review migration pass.

- [ ] **Step 7: Commit**

```powershell
git add -- vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerApplicationListMode.java vcampus-common/src/main/java/edu/seu/vcampus/common/shop/SellerApplicationQuery.java vcampus-common/src/test/java/edu/seu/vcampus/common/shop/SellerApplicationContractTest.java vcampus-server/src/main/java/edu/seu/vcampus/server/shop/repository/AccessShopRepository.java vcampus-server/src/test/java/edu/seu/vcampus/server/shop/repository/AccessShopRepositoryTest.java vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler/AdminShopHandlersTest.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ApplicationReviewPanel.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/admin/ApplicationReviewPanelTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/service/ShopClientServiceTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthEndToEndTest.java
git commit -m "feat(shop): split application review queues"
```

### Task 7: Responsive Catalog Cards and Search Semantics

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductCardContext.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductCardsPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/ProductGridPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/WrappingGridLayout.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ShopHomePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductSearchPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/BuyerShopPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/catalog/ProductGridPanelTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/CatalogPanelsTest.java`

**Interfaces:**
- Consumes: Task 5 `BuiltinProductImageLoader` and existing `ProductSummary`.
- Produces: fixed-size accessible whole-card details and context-specific shop-name visibility.

- [ ] **Step 1: Add failing layout and card-context tests**

```java
@Test
void firstAsyncRenderUsesOnlyRequiredRows() throws Exception {
    List<CompletableFuture<ImageIcon>> pending = new ArrayList<>();
    ProductImageLoader loader = (url, category, size) -> {
        CompletableFuture<ImageIcon> future = new CompletableFuture<>();
        pending.add(future);
        return future;
    };
    ProductGridPanel grid = new ProductGridPanel(loader, fixedHeightRenderer(220), id -> { });
    onEdt(() -> { grid.setSize(900, 600); grid.showProducts(products(8)); });
    pending.forEach(future -> future.complete(new ImageIcon(
            new BufferedImage(160, 110, BufferedImage.TYPE_INT_ARGB))));
    flushEdt();
    assertThat(grid.getPreferredSize().height).isEqualTo(2 * 220 + 12);
}

@Test
void homeHidesShopAndSearchShowsItWithoutDuplicateOpenButton() {
    JPanel home = renderOne(ProductCardContext.HOME);
    JPanel search = renderOne(ProductCardContext.SEARCH);
    assertThat(findNamed(home, "product-p1.shop")).isNull();
    assertThat(findNamed(search, "product-p1.shop")).isNotNull();
    assertThat(findNamed(search, "product-p1.open")).isNull();
}
```

Implement `products(int)`, `fixedHeightRenderer(int)`, `renderOne(ProductCardContext)`, and the recursive `findNamed` as private test helpers in the named test classes. `products(int)` must construct deterministic `ProductSummary` values; the renderer must return a panel with exactly the requested preferred height.

Add mouse and Enter-key activation tests for the card, and width tests proving wider containers increase columns while card width stays fixed.

- [ ] **Step 2: Run catalog tests to red**

```powershell
mvn -pl vcampus-client -am '-Dtest=ProductGridPanelTest,CatalogPanelsTest' test
```

Expected: the duplicate button exists, home displays shop name, cards stretch, and first async height differs from the final preferred height.

- [ ] **Step 3: Add scenario configuration and accessible whole-card activation**

```java
public enum ProductCardContext {
    HOME(false), SEARCH(true), STOREFRONT(false);
    private final boolean showShopName;
}
```

Use one focusable card panel. Attach one action to mouse click and the Enter/Space input map. Register the same mouse adapter on every passive image/label child so clicking the visible description area triggers the card; do not create nested action controls. Remove the secondary open button entirely.

- [ ] **Step 4: Make grid dimensions deterministic**

Keep card width fixed at 200. Calculate columns from available width, center the used columns, and calculate preferred height from `ceil(itemCount / columns)`. After each initial and async `renderInto`, call `slot.revalidate()`, `ProductGridPanel.this.revalidate()`, and revalidate the viewport parent on the EDT.

- [ ] **Step 5: Simplify search controls**

Remove `filterToggle`. Keep `filters` visible after search. Search submits the keyword; category/min/max only submit from `filterButton`; sort selection immediately builds a page-zero query. Preserve `restoring` so route restoration does not issue a request.

- [ ] **Step 6: Run focused catalog tests**

```powershell
mvn -pl vcampus-client -am '-Dtest=ProductGridPanelTest,CatalogPanelsTest,CatalogPaginationTest' test
```

Expected: first load and paging share one height path; fixed responsive columns, card activation, display contexts, delayed filters, and immediate sort pass.

- [ ] **Step 7: Commit**

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductCardContext.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductCardsPanel.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/ProductGridPanel.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/WrappingGridLayout.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ShopHomePanel.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductSearchPanel.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/BuyerShopPanel.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/catalog/ProductGridPanelTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/CatalogPanelsTest.java
git commit -m "fix(shop): make catalog cards responsive"
```

### Task 8: Cart Grid, Checkout List, and Authoritative Totals

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CartItemCard.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CheckoutItemRow.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/WrappingGridLayout.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CartPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CheckoutPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/PurchasePanelsTest.java`

**Interfaces:**
- Consumes: `CartView.displayedTotal`, `CartItemView`, Task 2 navigator, and Task 7 responsive layout.
- Produces: fixed responsive cart cards, read-only vertical checkout rows, and product-detail callbacks isolated from controls.

- [ ] **Step 1: Add failing amount, layout, and click-isolation tests**

```java
@Test
void cartDisplaysUnitQuantitySubtotalAndAuthoritativeTotal() {
    when(client.getCart()).thenReturn(completedFuture(new CartView("cart-1", List.of(
            new CartItemView("i1", "p1", "笔记本", "s1", "方格", "shop-1", "店铺",
                    new BigDecimal("3.35"), 4, 1)), new BigDecimal("13.40"))));
    onEdt(panel::load);
    flushEdt();
    assertThat(component(panel, "cart-item-i1.unit-price", JLabel.class).getText())
            .isEqualTo("单价：¥3.35");
    assertThat(component(panel, "cart-item-i1.subtotal", JLabel.class).getText())
            .isEqualTo("小计：¥13.40");
    assertThat(component(panel, "cart.total", JLabel.class).getText())
            .isEqualTo("总计：¥13.40");
}

@Test
void onlyCartProductRegionNavigatesToDetails() {
    onEdt(() -> component(panel, "cart-item-i1.product", JButton.class).doClick());
    assertThat(navigator.current()).contains(new ShopRoute.Product("p1"));
    onEdt(() -> component(panel, "cart-item-i1.update", JButton.class).doClick());
    verify(client).updateCartItem(any());
}
```

Add checkout tests for vertical row order, unit price, quantity, subtotal, total near submit, and submit disabled while in flight.

- [ ] **Step 2: Run purchase tests to red**

```powershell
mvn -pl vcampus-client -am -Dtest=PurchasePanelsTest test
```

Expected: current FlowLayout lacks subtotal/total and horizontal controls do not provide a product detail region.

- [ ] **Step 3: Implement reusable amount formatting**

Use one private or package-level formatter:

```java
static String money(BigDecimal amount) {
    return "¥" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
}

static BigDecimal subtotal(CartItemView item) {
    return item.displayedUnitPrice().multiply(BigDecimal.valueOf(item.quantity()));
}
```

The page total must use `CartView.displayedTotal`; never replace it with a locally summed checkout authority.

- [ ] **Step 4: Build fixed responsive cart cards**

Make `WrappingGridLayout` public with a public constructor so Cart can reuse it. `CartItemCard` uses vertical internal layout and accepts callbacks `openProduct`, `updateQuantity`, and `remove`. Represent the image/name/SKU description region as one borderless, vertically aligned `JButton` named `cart-item-<id>.product`; quantity, update, and remove remain separate sibling controls, so their events cannot trigger detail navigation.

- [ ] **Step 5: Build read-only checkout rows**

Use `BoxLayout.Y_AXIS` for `checkout.items`. `CheckoutItemRow` displays the same five facts without mutation controls. Put total and submit in a stable bottom summary panel. Preserve the current price-change confirmation and cashier lifecycle.

- [ ] **Step 6: Run focused purchase and navigation tests**

```powershell
mvn -pl vcampus-client -am '-Dtest=PurchasePanelsTest,ShopNavigatorTest,ShopUiTest' test
```

Expected: responsive multi-column cart, vertical checkout, all amounts, click isolation, and duplicate-submit protection pass.

- [ ] **Step 7: Commit**

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CartItemCard.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CheckoutItemRow.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/WrappingGridLayout.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CartPanel.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CheckoutPanel.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/PurchasePanelsTest.java
git commit -m "fix(shop): clarify cart and checkout totals"
```

### Task 9: Demo Documentation, Portable Package, and Integrated Acceptance

**Files:**
- Modify: `vcampus-distribution/templates/shop-auth-demo/使用说明.txt`
- Modify: `vcampus-distribution/SHOP_AUTH_DEMO_PACKAGE_USAGE.md`
- Modify: `vcampus-database/demo/SHOP_AUTH_DEMO.md`
- Modify: `vcampus-database/demo/SHOP_AUTH_DEMO_TEAM_TESTING.md`
- Modify: `vcampus-distribution/scripts/tests/build-shop-auth-demo-package.tests.ps1`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthEndToEndTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopSellerAdminEndToEndTest.java`
- Modify: `docs/superpowers/specs/2026-09-01-vcampus-shop-manual-test-findings.md`

**Interfaces:**
- Consumes: all prior tasks and existing package builder.
- Produces: one verified four-role portable Demo using port 8888 and all confirmed manual-test acceptance paths.

- [ ] **Step 1: Make release assertions fail on the old package**

Update package tests to require:

```powershell
Assert-Contains $serverArgs 'database\vcampus-shop-auth-demo.accdb 8888 database\schema database\seed'
Assert-Contains $clientArgs 'ShopAuthDemoClientMain 127.0.0.1 8888'
Assert-Contains $instructions '端口：8888'
Assert-Contains $instructions '每类提供 4 个内置封面'
```

Replace the obsolete `暂无图片` requirement with the built-in cover requirement.

- [ ] **Step 2: Run release tests to red**

```powershell
& .\vcampus-distribution\scripts\tests\build-shop-auth-demo-package.tests.ps1
```

Expected: packaged BAT files and instructions still contain `19090` and placeholder-only wording.

- [ ] **Step 3: Update all Shop Demo instructions**

Document port 8888, explicit override syntax, the conflict rule for another local server on 8888, four fixed accounts, multi-client testing, User Manager-owned account/logout flow, twenty built-in choices, and the complete manual regression sequence.

- [ ] **Step 4: Extend end-to-end tests across all roles**

Add deterministic scenarios for:

```java
// buyer: home -> cart -> my -> back -> home; totals; checkout; paid orders
// applicant: direct submit, admin latest-first pending review
// seller: create product with builtin cover and system-generated SKU ID
// admin: processed tab, shop/product management, buyer forbidden
```

Keep the server running once and use distinct client connections/sessions; do not share session tokens between clients.

- [ ] **Step 5: Run all focused Shop suites**

```powershell
mvn -pl vcampus-common,vcampus-server,vcampus-client -am test
& .\vcampus-distribution\scripts\tests\start-shop-auth-demo-scripts.tests.ps1
& .\vcampus-distribution\scripts\tests\build-shop-auth-demo-package.tests.ps1
```

Expected: every Common, Server, and Client test plus both Shop PowerShell suites passes; no test is weakened or skipped.

- [ ] **Step 6: Run the complete repository verification**

```powershell
mvn verify
git diff --check
git status --short
```

Expected: complete reactor verification passes; no whitespace errors; only intended Shop changes and preserved untracked `logs/`/brainstorm artifacts appear.

- [ ] **Step 7: Build and smoke-test the portable release**

```powershell
$zipPath = & .\vcampus-distribution\scripts\build-shop-auth-demo-package.ps1
$zipPath
Get-FileHash -Algorithm SHA256 -LiteralPath $zipPath
```

Extract to a new timestamped directory under `target`, start its server on 8888, wait for the startup banner, run one real client login/catalog request, then stop only the processes started by this checkpoint. Do not delete the extracted evidence or `logs/`.

- [ ] **Step 8: Perform the twelve-item manual regression**

Use the findings document as the checklist. Record for every item: account, path, expected result, observed result, screenshot/log reference if applicable, and PASS/FAIL. Also verify the unified User Manager login, first-password-change, account page, confirmed logout, and session-expiry handoff after an explicitly authorized joint integration with `8d53d9e`.

- [ ] **Step 9: Update findings with verification evidence**

Add a dated “整改验收结果” section. Do not erase the original observations. Mark an issue resolved only when its automated test and manual scenario both pass.

- [ ] **Step 10: Commit release evidence and documentation**

```powershell
git add -- vcampus-distribution/templates/shop-auth-demo/使用说明.txt vcampus-distribution/SHOP_AUTH_DEMO_PACKAGE_USAGE.md vcampus-database/demo/SHOP_AUTH_DEMO.md vcampus-database/demo/SHOP_AUTH_DEMO_TEAM_TESTING.md vcampus-distribution/scripts/tests/build-shop-auth-demo-package.tests.ps1 vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthEndToEndTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopSellerAdminEndToEndTest.java docs/superpowers/specs/2026-09-01-vcampus-shop-manual-test-findings.md
git commit -m "test(shop-demo): verify usability remediation"
```

## Execution Checkpoints

After every task:

1. Report the exact failing-test command and expected failure observed before implementation.
2. Report the exact passing-test command and result after implementation.
3. Run `git diff --check`.
4. Show `git status --short` and confirm `logs/` remains untracked.
5. Review only the current task diff before committing.

Before Task 9 final joint User Manager verification, stop and obtain explicit authorization if merging or otherwise introducing `origin/feat/user-management@8d53d9e` is required. Compatibility inspection is not merge permission.
