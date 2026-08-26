# VCampus UI Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the shared Swing design tokens, application shell, components, page-state infrastructure, accessibility checks, and screenshot-review fixtures required by all five business modules.

**Architecture:** A single `client.core.ui` layer owns visual tokens, layout templates, common components, asynchronous page-state handling, and the fixed `MainFrame` shell. Business modules compose these APIs and supply content and semantic state only; they do not subclass components to replace visual properties or create private themes.

**Tech Stack:** JDK 21, Maven, Swing, CompletableFuture, JUnit 5, AssertJ Swing, Java Accessibility API.

**Spec:** `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md` and `docs/superpowers/specs/2026-08-24-vcampus-overall-architecture-design.md`

## Global Constraints

- Implement exactly the colors, font roles, spacing, dimensions, borders, window sizes, and navigation order defined by the UI specification.
- Load bundled Source Han Serif first, then `SimSun`, `Songti SC`, and logical `Serif`; no visible text may be smaller than 12 logical pixels.
- Business pages must not contain raw colors, font sizes, spacing values, borders, `null` layouts, or `setBounds` calls.
- `MainFrame` defaults to 1280 × 800, has a minimum of 1024 × 680, and owns the 56 px header, 184 px permission-filtered navigation, `CardLayout` content, and 28 px status bar.
- The first-level navigation order is student profile, course center, library, shop, and account settings; do not add an unowned dashboard page.
- Every data page implements initial, loading, normal, empty, error, and disconnected states; write surfaces also implement submitting and concurrent-conflict states.
- Socket waits, database access, file reads, and expensive work never run on the EDT; stale or disposed-page results never mutate Swing components.
- The student-module UI owner maintains tokens, shared components, and the shell unless the team records one explicit owner transfer covering all three.
- No UI exception is valid unless it is added to the specification's exception register and approved by the team.

---

### Task 1: Design Tokens, Font Loading, and Visual Primitives

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/theme/UiColors.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/theme/UiTypography.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/theme/UiSpacing.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/theme/UiDimensions.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/theme/UiBorders.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/theme/UiThemeInstaller.java`
- Create: `vcampus-client/src/main/resources/fonts/SourceHanSerifSC-Regular.otf`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/theme/UiThemeTest.java`

**Interfaces:**
- Consumes: Swing `UIManager`, bundled font resource, platform font availability.
- Produces: immutable token constants and `UiThemeInstaller.install()` called once before creating any Swing window.

- [ ] **Step 1: Write exact token and fallback-order tests**

```java
@Test
void exposesTheSpecifiedAcademicPaletteAndDimensions() {
    assertThat(UiColors.BACKGROUND_PAGE).isEqualTo(Color.decode("#FBF7EF"));
    assertThat(UiColors.PRIMARY).isEqualTo(Color.decode("#163B33"));
    assertThat(UiColors.ACCENT).isEqualTo(Color.decode("#AD4432"));
    assertThat(UiDimensions.CONTROL_HEIGHT).isEqualTo(32);
    assertThat(UiDimensions.TABLE_ROW_HEIGHT).isEqualTo(40);
    assertThat(UiSpacing.PAGE_PADDING).isEqualTo(24);
}

@Test
void choosesTheFirstAvailableSerifFontInSpecifiedOrder() {
    assertThat(UiTypography.chooseFamily(Set.of("SimSun", "Serif")))
            .isEqualTo("SimSun");
}
```

- [ ] **Step 2: Run the theme tests and confirm failure**

Run: `mvn -pl vcampus-client -am -Dtest=UiThemeTest test`

Expected: FAIL because token and theme classes do not exist.

- [ ] **Step 3: Implement immutable tokens and one-time theme installation**

```java
public final class UiDimensions {
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 800;
    public static final int WINDOW_MIN_WIDTH = 1024;
    public static final int WINDOW_MIN_HEIGHT = 680;
    public static final int CONTROL_HEIGHT = 32;
    public static final int TABLE_ROW_HEIGHT = 40;
    private UiDimensions() { }
}
```

Register the bundled font with `GraphicsEnvironment.registerFont`, build `DISPLAY`, `PAGE_TITLE`, `SECTION_TITLE`, `BODY`, `TABLE_COMPACT`, and `CAPTION`, and apply shared Swing defaults before any frame is instantiated. Keep every numeric and color literal inside the token package.

- [ ] **Step 4: Run token tests and a raw-literal scan**

Run: `mvn -pl vcampus-client -am -Dtest=UiThemeTest test`

Run: `rg -n "new Color|Color\.(WHITE|BLUE|MAGENTA)|setFont\(new Font|setBorder\(BorderFactory" vcampus-client/src/main/java/edu/seu/vcampus/client --glob '!**/core/ui/theme/**'`

Expected: tests PASS and the scan returns no business-package matches.

- [ ] **Step 5: Commit the token layer**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/theme vcampus-client/src/main/resources/fonts vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/theme
git commit -m "feat(ui): add shared academic design tokens"
```

### Task 2: Shared Controls, Tables, Dialogs, and Page States

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/component/PrimaryButton.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/component/SecondaryButton.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/component/GhostButton.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/component/DangerButton.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/component/FilterPanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/component/PagedTablePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/component/StatusLabel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/component/ConfirmDialog.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/component/NotificationService.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/state/PageState.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/state/LoadingOverlay.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/state/EmptyStatePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/state/ErrorStatePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/state/DisconnectedStatePanel.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/state/ConflictStatePanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/component/SharedComponentsTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/state/PageStateTest.java`

**Interfaces:**
- Consumes: Task 1 tokens and semantic labels/data supplied by business pages.
- Produces: final or composition-only common controls, `PageState`, and state panels that business modules reuse without visual overrides.

- [ ] **Step 1: Write shared-behavior and state-transition tests**

```java
@Test
void tableUsesSpecifiedAlignmentPaginationAndEmptyState() {
    PagedTablePanel<Row> table = new PagedTablePanel<>(columns(), renderer());
    table.showPage(PageResult.empty(1, 20), "未找到课程", "请重置筛选条件");
    assertThat(table.headerHeight()).isEqualTo(UiDimensions.TABLE_ROW_HEIGHT);
    assertThat(table.paginationLabels()).containsExactly("共 0 条", "上一页", "1/1", "下一页");
    assertThat(table.emptyMessage()).contains("未找到课程");
}

@Test
void submittingLocksOnlyTheAffectedFormAndPreservesInputOnFailure() {
    StatefulFormHarness form = new StatefulFormHarness();
    form.submit().fail("请检查填写内容");
    assertThat(form.input()).isEqualTo("原输入");
    assertThat(form.navigationEnabled()).isTrue();
    assertThat(form.submitText()).isEqualTo("保存修改");
}
```

- [ ] **Step 2: Run component/state tests and confirm failure**

Run: `mvn -pl vcampus-client -am -Dtest=SharedComponentsTest,PageStateTest test`

Expected: FAIL because shared controls and state panels are absent.

- [ ] **Step 3: Implement the component contracts from sections 5 and 8**

```java
public enum PageState {
    INITIAL, LOADING, NORMAL, EMPTY, ERROR, DISCONNECTED, SUBMITTING, CONFLICT
}
```

Make buttons expose default, hover, focus, pressed, disabled, and loading behavior; preserve the 2 px visible focus border. Keep one page-region `PrimaryButton`, place dialog cancel left and confirm right, keep only horizontal table separators, and make status labels pair color with complete Chinese text.

- [ ] **Step 4: Run the shared UI test suite**

Run: `mvn -pl vcampus-client -am -Dtest=SharedComponentsTest,PageStateTest test`

Expected: PASS for button states, labels, pagination order, dialog widths, notifications, all eight page states, and failure-input preservation.

- [ ] **Step 5: Commit shared components**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/component vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/state vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui
git commit -m "feat(ui): add shared controls and page states"
```

### Task 3: Fixed Application Shell and Five Page Templates

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/navigation/PageNavigator.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/shell/IdentityHeader.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/shell/PermissionNavigation.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/shell/ApplicationStatusBar.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/template/SearchListPage.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/template/DetailPage.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/template/EditFormPage.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/template/ManagementPage.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/template/ShowcasePage.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/MainFrameLayoutTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/PageTemplateTest.java`

**Interfaces:**
- Consumes: authenticated identity, permissions, connection state, Task 1 tokens, and Task 2 components.
- Produces: fixed shell regions, `PageNavigator.show(String pageId)`, and five composition templates used by every business page.

- [ ] **Step 1: Write window, navigation, permission, and template tests**

```java
@Test
void shellHasFixedStructureAndPermissionFilteredNavigation() {
    MainFrame frame = launchMainFrame(studentIdentity());
    assertThat(frame.getSize()).isEqualTo(new Dimension(1280, 800));
    assertThat(frame.getMinimumSize()).isEqualTo(new Dimension(1024, 680));
    assertThat(frame.navigationLabels()).containsExactly(
            "学籍档案", "课程中心", "图书借阅", "校园商城", "账户设置");
    assertThat(frame.navigationLabels()).doesNotContain("总览", "账户管理");
}
```

- [ ] **Step 2: Run shell/template tests and confirm failure**

Run: `mvn -pl vcampus-client -am -Dtest=MainFrameLayoutTest,PageTemplateTest test`

Expected: FAIL because the specification-complete shell and templates do not exist.

- [ ] **Step 3: Implement shell dimensions, permission filtering, and templates**

```java
setPreferredSize(new Dimension(UiDimensions.WINDOW_WIDTH, UiDimensions.WINDOW_HEIGHT));
setMinimumSize(new Dimension(UiDimensions.WINDOW_MIN_WIDTH, UiDimensions.WINDOW_MIN_HEIGHT));
setLayout(new BorderLayout());
add(identityHeader, BorderLayout.NORTH);
add(permissionNavigation, BorderLayout.WEST);
add(cardContent, BorderLayout.CENTER);
add(applicationStatusBar, BorderLayout.SOUTH);
```

The shell alone owns its colors and dimensions. Templates expose named slots for breadcrumbs, the single primary action, filters, summaries, content, pagination, field groups, and form actions; business pages may add content only through those slots.

- [ ] **Step 4: Run layout tests at both required sizes**

Run: `mvn -pl vcampus-client -am -Dtest=MainFrameLayoutTest,PageTemplateTest test`

Expected: PASS at 1024 × 680 and 1280 × 800 with no overlap, clipping, hidden primary action, unauthorized navigation, or absolute positioning.

- [ ] **Step 5: Commit shell and templates**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/core/{navigation,ui} vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui
git commit -m "feat(ui): implement the shared application shell"
```

### Task 4: Latest-Request Async Binding, Lifecycle Safety, and Accessibility

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/async/LatestRequestController.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/async/PageLifecycle.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/accessibility/FocusOrderPolicy.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/async/LatestRequestControllerTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/accessibility/UiAccessibilityTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/ResponsiveLayoutTest.java`

**Interfaces:**
- Consumes: `CompletableFuture<T>` from client services and page lifecycle events.
- Produces: `submit(Supplier<CompletableFuture<T>>, Consumer<T>, Consumer<Throwable>)`, disposal guards, deterministic focus traversal, and reusable layout probes.

- [ ] **Step 1: Write stale-result, disposal, focus, and long-text tests**

```java
@Test
void ignoresOlderResponseAndResponseForDisposedPage() {
    controller.submit(() -> first, view::render, view::showError);
    controller.submit(() -> second, view::render, view::showError);
    first.complete(oldResult);
    second.complete(newResult);
    assertThat(view.rendered()).containsExactly(newResult);
    controller.dispose();
    controller.submit(() -> completedFuture(lateResult), view::render, view::showError);
    assertThat(view.rendered()).containsExactly(newResult);
}
```

- [ ] **Step 2: Run async/accessibility tests and confirm failure**

Run: `mvn -pl vcampus-client -am -Dtest=LatestRequestControllerTest,UiAccessibilityTest,ResponsiveLayoutTest test`

Expected: FAIL because lifecycle and accessibility infrastructure is absent.

- [ ] **Step 3: Implement generation-based response acceptance and focus rules**

```java
long generation = requestGeneration.incrementAndGet();
future.whenComplete((value, error) -> SwingUtilities.invokeLater(() -> {
    if (disposed || generation != requestGeneration.get()) return;
    if (error == null) onSuccess.accept(value); else onFailure.accept(error);
}));
```

Give every icon-only exception an accessible name, pair required/error/status colors with text, move dialog focus inside on open, restore it on close, and keep Tab order equal to visual reading order.

- [ ] **Step 4: Verify required dimensions, 150% scale, and EDT safety**

Run: `mvn -pl vcampus-client -am -Dtest=LatestRequestControllerTest,UiAccessibilityTest,ResponsiveLayoutTest,EdtSafetyTest test`

Expected: PASS for newest-response wins, disposed-page safety, visible focus, dialog focus restoration, long Chinese text, 1024 × 680, 1280 × 800, and 150% UI scaling.

- [ ] **Step 5: Commit async and accessibility infrastructure**

```bash
git add vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/{async,accessibility} vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui
git commit -m "feat(ui): enforce async lifecycle and accessibility"
```

### Task 5: Compliance Audit, Screenshot Fixtures, and Merge Gate

**Files:**
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/audit/UiComplianceAudit.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/audit/UiAuditResult.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/audit/UiComplianceTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/audit/UiScreenshotFixture.java`
- Create: `docs/ui-review/README.md`
- Create: `docs/ui-review/manifest.md`

**Interfaces:**
- Consumes: registered business pages, deterministic Chinese demonstration data, and a connected demo session.
- Produces: `UiAuditResult inspect(Collection<? extends Component> pages)`, automated structural audit, and a manifest for normal/loading/empty/error screenshots at 1280 × 800 and 100% scale.

- [ ] **Step 1: Write the failing cross-module compliance audit**

```java
public record UiAuditResult(
        List<String> privateThemeClasses,
        List<String> absoluteLayoutUsages,
        List<String> pagesWithoutTemplate,
        List<String> pagesMissingRequiredStates,
        List<String> regionsWithMultiplePrimaryButtons,
        List<String> inaccessibleControls,
        List<String> staleOrDisposedAsyncUpdates,
        List<String> disallowedImageGrids) { }

@Test
void everyRegisteredBusinessPagePassesTheUiMergeGate() {
    UiAuditResult result = UiComplianceAudit.inspect(PageRegistry.all());
    assertThat(result.privateThemeClasses()).isEmpty();
    assertThat(result.absoluteLayoutUsages()).isEmpty();
    assertThat(result.pagesWithoutTemplate()).isEmpty();
    assertThat(result.pagesMissingRequiredStates()).isEmpty();
    assertThat(result.regionsWithMultiplePrimaryButtons()).isEmpty();
    assertThat(result.inaccessibleControls()).isEmpty();
    assertThat(result.staleOrDisposedAsyncUpdates()).isEmpty();
    assertThat(result.disallowedImageGrids()).isEmpty();
}
```

- [ ] **Step 2: Run the audit and confirm it identifies unregistered pages**

Run: `mvn -pl vcampus-client -am -Dtest=UiComplianceTest test`

Expected: FAIL and list each missing page registration or compliance violation by class name.

- [ ] **Step 3: Implement the audit and deterministic screenshot manifest**

```markdown
| Module | Page | Normal | Loading | Empty | Error | Reviewer |
|---|---|---|---|---|---|---|
| user | LoginFrame | user-login-normal.png | user-login-loading.png | n/a | user-login-error.png | student-module-owner |
```

The fixture fixes the window at 1280 × 800, scale at 100%, demo user/data/connection state, and filenames as `<module>-<page>-<state>.png`. Every new page records normal, loading, empty, and error captures where the state applies; each module requires a non-owner reviewer before merge.

- [ ] **Step 4: Run the complete client UI gate**

Run: `mvn -pl vcampus-client -am verify`

Run: `rg -n "setLayout\(null\)|setBounds\(|#[0-9A-Fa-f]{6}|new Color|new Font" vcampus-client/src/main/java/edu/seu/vcampus/client --glob '!**/core/ui/theme/**'`

Expected: BUILD SUCCESS; the scan returns no business-page matches, all pages are registered, and the manifest has no missing applicable state or reviewer field.

- [ ] **Step 5: Commit the UI merge gate**

```bash
git add vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/audit docs/ui-review
git commit -m "test(ui): add cross-module visual compliance gate"
```
