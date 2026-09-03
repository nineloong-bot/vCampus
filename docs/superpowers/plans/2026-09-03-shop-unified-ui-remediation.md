# Shop Unified UI Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Do not use subagents for this repository. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every Shop buyer, seller, and administrator screen visually consistent with the repository's VCampus UI design system without changing Shop behavior, protocol, database schema, routes, or authorization.

**Architecture:** Keep `ShopUiKit` as the semantic boundary. Add a shared-theme adapter backed only by `client.core.ui.theme` tokens, add small Shop-local styling helpers for tables/forms/dialogs, then migrate each page group in test-driven checkpoints. The unified application changes one composition line from `DefaultShopUiKit` to `SharedShopUiKitAdapter`; `DefaultShopUiKit` remains an unstyled compatibility implementation.

**Tech Stack:** Java 21, Swing, Maven, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md`; Shop boundary: `docs/superpowers/specs/2026-08-29-vcampus-shop-authenticated-buyer-demo-design.md` section 6.1.

## Global Constraints

- Work only in `E:\summer-school\vCampus\.worktrees\shop-auth-demo`; verify branch and status before every checkpoint.
- Preserve `logs/`, `.superpowers/brainstorm/`, all existing untracked findings, `artifacts/`, and the runtime-modified `vcampus-distribution/data/vCampus.accdb`.
- Modify UI code only. Do not change Shop protocol DTOs, routes, services, handlers, authorization, database, sample data, public network APIs, or port `8888`.
- Do not redesign `MainFrame`, Foundation, User, Course, Library, Socket, Router, or transaction infrastructure.
- The only permitted Shop-package-external source edit is replacing the Shop UI adapter construction in `client/core/ui/MainFrame.java`. If that one-line integration edit is not authorized, stop: the unified launcher cannot receive the new theme.
- Use only `UiColors`, `UiTypography`, `UiSpacing`, and `UiBorders`; do not introduce private RGB values, private fonts, arbitrary spacing constants, gradients, shadows, rounded cards, or external images.
- Preserve all existing component `name` values because tests and UI automation depend on them.
- Each visible region has at most one brick-red primary action. Secondary actions use dark-green outline styling; destructive actions use the shared error palette and explicit Chinese text.
- Standard controls are 32 px high; compact table toolbars may be 28 px. Standard table rows/headers are 40 px, compact management tables 34 px.
- All user-visible text remains Chinese. Existing business behavior and accepted manual-test fixes must remain unchanged.
- Every implementation task follows RED → verify correct failure → minimal GREEN → focused regression → checkpoint report. Do not commit, push, merge, rebase, delete, clean, or revert unless the user separately authorizes it.

---

### Task 1: Lock the Shop theme contract with failing tests

**Files:**
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/style/ShopUiKitTest.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/style/ShopComponentStyleTest.java`

**Interfaces:**
- Produces: executable assertions for `SharedShopUiKitAdapter`, `ShopComponentStyle.styleTable(JTable, boolean)`, `styleTextComponent(JComponent)`, `styleTabbedPane(JTabbedPane)`, and `pagePanel(LayoutManager)`.

- [ ] Add tests asserting that `SharedShopUiKitAdapter.primaryButton(...)` keeps its name/text and uses `UiColors.ACCENT`, `UiColors.TEXT_ON_PRIMARY`, `UiTypography.BODY_BOLD`, a square border, and 32 px preferred height.
- [ ] Add parallel assertions for navigation and secondary buttons: navigation uses the dark-green semantic treatment; secondary uses paper background, dark-green foreground, and a one-pixel shared border.
- [ ] Assert that filter panels use `BACKGROUND_SUBTLE`, product cards use `BACKGROUND_PAGE` plus `UiBorders.LINE`, and state views use state-appropriate shared colors while retaining Chinese message and retry action.
- [ ] Assert standard and compact table row/header heights (`40` and `34`), horizontal-line-only grid treatment, shared fonts/colors, single-row selection, and no column reordering.
- [ ] Assert page panels use `BACKGROUND_PAGE` and `UiBorders.pageInset()`, and tabbed panes use shared body typography/colors.
- [ ] Run:

  `mvn -pl vcampus-client -am -Dtest=ShopUiKitTest,ShopComponentStyleTest test`

  Expected RED: compilation failure because `SharedShopUiKitAdapter` and `ShopComponentStyle` do not exist. Record this exact cause before implementation.

### Task 2: Implement the shared Shop adapter and styling helpers

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/style/SharedShopUiKitAdapter.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/style/ShopComponentStyle.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/style/ShopUiKit.java`
- Test: files from Task 1

**Interfaces:**
- `SharedShopUiKitAdapter implements ShopUiKit` and implements the existing six semantic factory methods without changing their signatures.
- `ShopComponentStyle` is a non-instantiable Shop-local utility exposing `pagePanel`, `styleTable`, `styleTextComponent`, `styleTabbedPane`, `styleScrollPane`, and `styleDialogContent`.

- [ ] Extend `ShopUiKit` only with backward-compatible default methods when a semantic role is genuinely missing; do not add colors, fonts, or Swing look-and-feel selection to page classes.
- [ ] Implement all adapter factories using shared tokens. Use a common internal button styler so default, disabled, focus, and semantic colors do not drift.
- [ ] Map `ShopPageState`: normal/initial use normal page colors; loading/submitting use subtle background; empty uses secondary text; error/disconnected use `ERROR_BG`/`ERROR_FG`. Keep text and retry controls visible so color is never the sole signal.
- [ ] Implement table styling using cell renderers for alignment: text left, money/quantity right, status/short ID centered. Do not change model values.
- [ ] Keep `DefaultShopUiKit` behavior and its existing tests unchanged as the UIManager compatibility path.
- [ ] Run the Task 1 command. Expected GREEN: both style test classes pass.
- [ ] Run `mvn -pl vcampus-client -am -Dtest=ShopUiTest test`. Expected GREEN: semantic names and existing behavior remain intact.

### Task 3: Connect the shared adapter to both launch paths

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java:140-141`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMain.java:121`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`

**Interfaces:**
- Consumes: zero-argument `new SharedShopUiKitAdapter()` from Task 2.
- Produces: identical adapter selection in the unified client and standalone Shop demo.

- [ ] Add a source/composition test that asserts both launch paths reference `SharedShopUiKitAdapter` and no longer instantiate `DefaultShopUiKit` for production composition.
- [ ] Run the focused test and verify RED because both launch paths still instantiate `DefaultShopUiKit`.
- [ ] Replace only those two constructor calls and imports. Do not otherwise alter `MainFrame`.
- [ ] Run `mvn -pl vcampus-client -am -Dtest=ShopUiTest,ShopAuthDemoClientMainTest test`. Expected GREEN.

### Task 4: Unify the buyer catalog and navigation pages

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopModulePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopToolbar.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ShopHomePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductSearchPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductDetailPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/BuyerShopPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ProductCardsPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/ShopPaginationPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/catalog/DefaultProductCardRenderer.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/CatalogPanelsTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/CatalogPaginationTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/catalog/ProductGridPanelTest.java`

**Interfaces:**
- Consumes: Task 2 adapter factories and component styling helpers.
- Produces: search-list and showcase templates with unchanged Shop navigation callbacks and request state.

- [ ] Write failing structural tests for: 24 px page inset, page-title typography, subtle filter band, one red primary action, shared pagination styling, and a responsive 3–4-column product grid at the 1280 px baseline.
- [ ] Verify RED only on visual/layout assertions; existing navigation and catalog behavior must still pass.
- [ ] Apply the query-list structure `title/action → filter → result summary → grid → pagination`; retain all existing component names and state holders.
- [ ] Apply the detail structure `back/breadcrumb → product summary → status/action → grouped fields`. Keep “进入店铺”, variant choice, quantity, cart, and loading/error flows unchanged.
- [ ] Style product cards through the kit/helper. Preserve built-in placeholder imagery; do not add image dependencies or network requirements.
- [ ] Run the three focused test classes, then `ShopNavigatorTest` and `ShopNavigationStateTest`. Expected GREEN.

### Task 5: Unify cart, checkout, payment, and buyer order pages

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CartPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CartItemCard.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CheckoutPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/CheckoutItemRow.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/PaymentResultPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/SimulatedCashierDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/MyShopPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/PurchasePanelsTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/MyShopRoleActionTest.java`

- [ ] Add failing layout assertions for row-style cart items, a single checkout summary/action region, grouped checkout fields, clearly distinct payment states, and consistent order rows.
- [ ] Preserve default-unchecked cart selection, partial checkout, quantity editing, deletion, price-change confirmation, payment retry, and error-state data retention.
- [ ] Remove raw `new JButton(...)` construction from `CheckoutItemRow`; obtain semantic buttons from the injected kit without changing component names or listeners.
- [ ] Use one red primary action per view: “结算所选商品”, “提交订单”, payment success action, or the current order action. Keep other actions secondary and removal/destructive actions explicit.
- [ ] Make the cashier dialog modal and consistently sized from its content while preserving all simulated outcomes.
- [ ] Run `mvn -pl vcampus-client -am -Dtest=PurchasePanelsTest,MyShopRoleActionTest test`. Expected GREEN.

### Task 6: Unify seller application, profile, products, and orders

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SellerWorkspacePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SellerApplicationPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SwingSellerApplicationDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ShopProfilePanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ProductManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/ProductEditorPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SwingProductEditorDialogs.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SkuEditorDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/CoverPresetPickerPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/seller/SellerOrdersPanel.java`
- Test: existing seller UI test classes under `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/`

- [ ] Add failing tests for shared tab styling, 34 px management tables, split “上架商品/下架商品” columns, draft state in the inactive column, and disabled draft-listing operation.
- [ ] Add failing dialog tests for 560/720 px design widths, 32 px controls, consistent label alignment, shared title/section fonts, and cancel-left/primary-right button ordering.
- [ ] Keep the accepted application interaction intact: modal editor, save draft/direct submit, close-time draft prompt, gray character-limit hint disappearing after first input, and remaining-character counter at top right.
- [ ] Style product management as a management page; preserve the two-column active/inactive model and “草稿不能上架”. Ensure “添加商品种类” remains visible in both create and edit flows.
- [ ] Replace the raw confirm/cancel buttons in `SwingProductEditorDialogs` with kit buttons and change ambiguous visible “确定” text to the exact action phrase appropriate to the dialog, without changing its command behavior.
- [ ] Run all seller UI tests. Expected GREEN with no service calls or DTOs changed.

### Task 7: Unify administrator review and governance pages

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ShopAdminPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ApplicationReviewPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ApplicationDetailDialog.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/AdminProductManagementPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/admin/ShopStatusPanel.java`
- Test: existing admin UI test classes under `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/admin/`

- [ ] Add failing tests for shared tabs/tables, explicit selected-row actions, detail-dialog styling, and title/filter/action/table hierarchy.
- [ ] Preserve the accepted review behavior: single-click selects a pending or processed row and enables main-page approve/reject; double-click opens a read-only modal that can approve/reject; refresh reloads both tables.
- [ ] Replace raw approve/reject/close buttons in `ApplicationDetailDialog` with semantic kit buttons. Use explicit Chinese actions and shared danger styling for rejection.
- [ ] Keep admin product management aligned with the seller management layout already accepted under SHOP-TEST-010/015; do not alter permission checks or management queries.
- [ ] Run `ApplicationReviewPanelTest`, `AdminProductManagementPanelTest`, and `ShopStatusPanelTest`. Expected GREEN.

### Task 8: Automated compliance audit and regression

**Files:**
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/style/ShopUiKitTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`
- Modify: `docs/ui-review/manifest.md`

- [ ] Extend the source audit across all `client/shop/ui` business pages: reject `java.awt.Color`, `new Color`, `new Font`, private RGB/hex values, and page-owned `BorderFactory` styling except inside `ui/style` and the built-in placeholder image renderer.
- [ ] Audit visible Shop strings for accidental “SKU” and ambiguous main-action “确定”; allow internal class/field/protocol identifiers to remain unchanged.
- [ ] Run focused Shop client tests:

  `mvn -pl vcampus-client -am -Dtest=ShopUiKitTest,ShopComponentStyleTest,ShopUiTest,CatalogPanelsTest,CatalogPaginationTest,ProductGridPanelTest,PurchasePanelsTest,MyShopRoleActionTest,ProductManagementPanelTest,SellerApplicationPanelTest,SellerApplicationSummaryTest,SellerOrdersPanelTest,SellerWorkspacePanelTest,SwingProductEditorDialogsTest,SwingSellerApplicationDialogTest,ApplicationReviewPanelTest,AdminProductManagementPanelTest,ShopStatusPanelTest test`

- [ ] Run the complete client module suite:

  `mvn -pl vcampus-client -am test`

- [ ] Run `git diff --check` and `git status --short`; report existing unrelated/untracked files separately and do not alter them.
- [ ] Update `docs/ui-review/manifest.md` with exact Shop screenshot scenarios and filenames; do not claim screenshots exist until captured.

### Task 9: Manual visual acceptance in the unified application

**Files:**
- Evidence only: Shop screenshots referenced by `docs/ui-review/manifest.md`

- [ ] Reset/start using the packaged scripts in this order: `reset-data.bat` only when a clean test database is desired; then `start-server-with-data.bat`; after port 8888 is listening, run `start-client.bat`.
- [ ] At 1280×800, capture buyer normal/loading/empty/error views for home, search, detail, storefront, cart, checkout, payment result, and orders.
- [ ] Capture seller application/draft, profile, split product management, product editor with visible “添加商品种类”, and seller orders.
- [ ] Capture administrator pending/processed applications, read-only review dialog, product management, and shop status management.
- [ ] Repeat representative home, management table, and complex editor checks at 1024×680 and Windows 150% display scaling. Confirm no clipped controls, overlap, unreadable text, or inaccessible scrollbar content.
- [ ] Compare Shop beside User, Course/Student, and Library screens. Check page titles, background, fonts, filter bands, buttons, table density, dialogs, empty/error states, and whitespace—not pixel identity of business-specific layouts.
- [ ] Re-run accepted Shop behaviors: partial cart payment, cart deletion, application draft/submit/close prompt, admin refresh and row review, product variant addition, active/inactive split, draft cannot list, and Chinese errors.
- [ ] Record every discrepancy as a new manual-test finding. Final acceptance remains the user's manual decision.

## Ordered Checkpoints for the Executor

1. Theme contract RED/GREEN.
2. Unified and demo composition RED/GREEN.
3. Buyer catalog RED/GREEN.
4. Purchase flow RED/GREEN.
5. Seller workspace RED/GREEN.
6. Administrator workspace RED/GREEN.
7. Full automated regression and diff audit.
8. Manual screenshot and behavior acceptance.

At every checkpoint report: changed files, failing-test command and correct failure reason, passing-test command and result counts, known risks, and the next manual screen to inspect. Stop immediately if a failure requires changing network, authorization, database, or another module's implementation.
