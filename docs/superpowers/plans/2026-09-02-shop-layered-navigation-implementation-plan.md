# vCampus Shop Layered Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Subagents and parallel agents are prohibited for this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the bounded serial Shop route history with a fixed semantic-layer model, and provide two equivalent fixed actions that always return to the default Shop home.

**Architecture:** Introduce one immutable, package-private `ShopNavigationState` that owns the content path, utility/checkout overlay path, and terminal payment receipt. `ShopNavigator` remains the only public navigation owner and delegates pure transitions to that state while retaining capture, rendering, listener, and leave-guard responsibilities. The sidebar “校园商城” entry and toolbar “返回首页” button both call one coordinator reset action.

**Tech Stack:** Java 21, Swing, Maven multi-module build, JUnit 5, AssertJ, Mockito, PowerShell Demo verification scripts.

**Spec:** `docs/superpowers/specs/2026-09-02-vcampus-shop-layered-navigation-design.md`

## Global Constraints

- Work only in `E:\summer-school\vCampus\.worktrees\shop-auth-demo` on branch `SHOP`.
- Before every execution batch report worktree, branch, HEAD, uncommitted files, and `origin/SHOP` tracking state.
- Preserve untracked `logs/`; do not delete, clean, stage, or commit it.
- Do not stage or commit `.superpowers/brainstorm/`.
- Do not use subagents or parallel agents; all work is performed by the current primary conversation.
- Modify only `vcampus-client/.../shop`, its Shop tests, Shop Demo/tests, and Shop documentation needed by this navigation change.
- Foundation, User, Socket, Router, transaction framework, and public networking implementations are dependency-only.
- Do not push, merge, rebase, delete, roll back, or clean files without explicit fresh authorization.
- Follow strict TDD for every behavior: add the focused failing test, run it and confirm the expected failure, implement the minimum behavior, then rerun the focused test.
- The sidebar “校园商城” and toolbar “返回首页” must share one reset action: default Home query, page index `0`, scroll offset `0`, and no restorable pre-reset route.
- A forced home reset must not clear cart, order, application draft, payment, or any other server-side business data.
- Seller-application unsaved-change protection remains authoritative for user-initiated navigation; an old asynchronous guard callback must not overwrite a newer transition.
- A completed payment must remove `Checkout` from every restorable path.
- Keep business state, navigation, loading, and Swing rendering separable.
- Make local commits only; never push.

## File Structure

New focused type:

- `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigationState.java`: immutable fixed-slot navigation state and pure transition rules.
- `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigationStateTest.java`: direct semantic-layer tests independent of Swing and route rendering.

Existing responsibilities retained or narrowed:

- `ShopRoute.java`: provides the one canonical default Home route used by all forced-home exits.
- `ShopNavigator.java`: leave guards, host capture/render, listeners, and delegation to `ShopNavigationState`.
- `ShopPageCoordinator.java`: owns `goHome()`, synchronizes cart count, and installs the toolbar callback.
- `ShopToolbar.java`: renders the fixed `shop.return-home` action to the left of `shop.my`; it does not implement reset rules itself.
- `ShopUiInstaller.java`: maps the existing shell `navigation.shop` entry to `InstalledCoordinator.goHome()`.
- `PaymentResultPanel.java` and `SimulatedCashierDialog.java`: use the layered navigation terminal/reset operations instead of serial-history replacements.

---

### Task 1: Pure Fixed-Slot Navigation State

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigationState.java`
- Create: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigationStateTest.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopRoute.java`

**Interfaces:**
- Consumes: the existing sealed `ShopRoute` records.
- Produces: `ShopRoute.defaultHome()`; package-private immutable operations `empty()`, `open(...)`, `replaceVisible(...)`, `captureVisible(...)`, `back()`, `reset(...)`, `openFromRoot(...)`, `completeCheckout(...)`, `current()`, `backTargets()`, `canGoBack()`, and `nodeCount()`.

- [ ] **Step 1: Write failing tests for content layers and canonical Home**

Create `ShopNavigationStateTest` with these first tests:

```java
package edu.seu.vcampus.client.shop.ui.navigation;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ShopNavigationStateTest {
    @Test
    void productAndDiscoveryRoutesReplaceTheirSemanticLayer() {
        ShopRoute.Home home = ShopRoute.defaultHome();
        ShopRoute.Search search = new ShopRoute.Search(search("笔"));
        ShopNavigationState state = ShopNavigationState.empty()
                .open(home)
                .open(search)
                .open(new ShopRoute.Product("p-1"))
                .open(new ShopRoute.Product("p-2"));

        assertThat(state.current()).contains(new ShopRoute.Product("p-2"));
        assertThat(state.backTargets()).containsExactly(home, search);
        state = state.back();
        assertThat(state.current()).contains(search);
        state = state.open(new ShopRoute.Storefront("shop-1"));
        assertThat(state.backTargets()).containsExactly(home);
    }

    @Test
    void repeatedStoreAndProductBrowsingNeverGrowsTheState() {
        ShopNavigationState state = ShopNavigationState.empty().open(ShopRoute.defaultHome());
        for (int index : IntStream.range(0, 100).toArray()) {
            state = state.open(new ShopRoute.Storefront("shop-" + index));
            state = state.open(new ShopRoute.Product("product-" + index));
        }

        assertThat(state.nodeCount()).isEqualTo(3);
        assertThat(state.backTargets()).hasSize(2);
        assertThat(state.back().back().current()).contains(ShopRoute.defaultHome());
    }

    @Test
    void defaultHomeAlwaysUsesFirstPageAndTopScroll() {
        ShopRoute.Home home = ShopRoute.defaultHome();

        assertThat(home.query().pageNumber()).isZero();
        assertThat(home.query().pageSize()).isEqualTo(20);
        assertThat(home.state().scrollY()).isZero();
    }
}
```

Add this private fixture method in the test file rather than a new production helper:

```java
private static ProductSearchQuery search(String keyword) {
    return new ProductSearchQuery(keyword, null, null, null,
            ProductSortMode.SALES_DESC, 0, 20);
}
```

- [ ] **Step 2: Run the focused test and observe the expected red state**

Run:

```powershell
mvn -pl vcampus-client -am '-Dtest=ShopNavigationStateTest' test
```

Expected: test compilation fails because `ShopNavigationState` and `ShopRoute.defaultHome()` do not exist. Confirm that these are the only intended failures before implementation.

- [ ] **Step 3: Add the canonical default Home route**

Add to `ShopRoute`:

```java
static Home defaultHome() {
    return new Home(new HomeProductQuery(null, null,
            ProductSortMode.SALES_DESC, 0, 20));
}
```

Do not add another default query constant in a Swing panel.

- [ ] **Step 4: Implement the immutable state shape and content transitions**

Create `ShopNavigationState` with these exact slots, constructor, and transition methods:

```java
final class ShopNavigationState {
    private final ShopRoute.Home home;
    private final ShopRoute discovery;
    private final ShopRoute.Product detail;
    private final ShopRoute utilityRoot;
    private final ShopRoute utilityChild;
    private final ShopRoute.Product preview;
    private final ShopRoute.PaymentResult receipt;

    private ShopNavigationState(ShopRoute.Home home, ShopRoute discovery,
            ShopRoute.Product detail, ShopRoute utilityRoot, ShopRoute utilityChild,
            ShopRoute.Product preview, ShopRoute.PaymentResult receipt) {
        if (discovery != null && !(discovery instanceof ShopRoute.Search)
                && !(discovery instanceof ShopRoute.Storefront)) {
            throw new IllegalArgumentException("invalid discovery route");
        }
        if (utilityRoot != null && !(utilityRoot instanceof ShopRoute.My)
                && !(utilityRoot instanceof ShopRoute.Cart)) {
            throw new IllegalArgumentException("invalid utility root");
        }
        if (utilityChild instanceof ShopRoute.Checkout
                && !(utilityRoot instanceof ShopRoute.Cart)) {
            throw new IllegalArgumentException("checkout requires cart root");
        }
        if ((utilityChild instanceof ShopRoute.SellerApplication
                || utilityChild instanceof ShopRoute.SellerWorkspace
                || utilityChild instanceof ShopRoute.AdminWorkspace)
                && !(utilityRoot instanceof ShopRoute.My)) {
            throw new IllegalArgumentException("workspace requires my root");
        }
        if (utilityChild != null && !(utilityChild instanceof ShopRoute.Checkout)
                && !(utilityChild instanceof ShopRoute.SellerApplication)
                && !(utilityChild instanceof ShopRoute.SellerWorkspace)
                && !(utilityChild instanceof ShopRoute.AdminWorkspace)) {
            throw new IllegalArgumentException("invalid utility child");
        }
        if (preview != null && !(utilityRoot instanceof ShopRoute.Cart)) {
            throw new IllegalArgumentException("preview requires cart root");
        }
        this.home = home;
        this.discovery = discovery;
        this.detail = detail;
        this.utilityRoot = utilityRoot;
        this.utilityChild = utilityChild;
        this.preview = preview;
        this.receipt = receipt;
    }

    static ShopNavigationState empty() {
        return new ShopNavigationState(null, null, null, null, null, null, null);
    }

    Optional<ShopRoute> current() {
        if (receipt != null) return Optional.of(receipt);
        if (preview != null) return Optional.of(preview);
        if (utilityChild != null) return Optional.of(utilityChild);
        if (utilityRoot != null) return Optional.of(utilityRoot);
        if (detail != null) return Optional.of(detail);
        if (discovery != null) return Optional.of(discovery);
        return Optional.ofNullable(home);
    }

    ShopNavigationState open(ShopRoute route) {
        Objects.requireNonNull(route, "route");
        ShopNavigationState base = home == null ? reset(ShopRoute.defaultHome()) : this;
        return switch (route) {
            case ShopRoute.Home value -> base.reset(value);
            case ShopRoute.Search value -> base.openDiscovery(value);
            case ShopRoute.Storefront value -> base.openDiscovery(value);
            case ShopRoute.Product value -> base.openProduct(value);
            case ShopRoute.My value -> base.openUtility(value);
            case ShopRoute.Cart value -> base.openUtility(value);
            case ShopRoute.SellerApplication value -> base.openMyChild(value);
            case ShopRoute.SellerWorkspace value -> base.openMyChild(value);
            case ShopRoute.AdminWorkspace value -> base.openMyChild(value);
            case ShopRoute.Checkout value -> base.openCheckout(value);
            case ShopRoute.PaymentResult value -> base.completeCheckout(value);
        };
    }

    ShopNavigationState replaceVisible(ShopRoute route) {
        Objects.requireNonNull(route, "route");
        ShopRoute visible = current().orElseThrow();
        if (visible.equals(route)) return this;
        if (visible instanceof ShopRoute.Home && route instanceof ShopRoute.Home value) {
            return new ShopNavigationState(value, discovery, detail, utilityRoot,
                    utilityChild, preview, receipt);
        }
        if (visible instanceof ShopRoute.Search && route instanceof ShopRoute.Search value) {
            return new ShopNavigationState(home, value, detail, utilityRoot,
                    utilityChild, preview, receipt);
        }
        if (visible instanceof ShopRoute.Storefront
                && route instanceof ShopRoute.Storefront value) {
            return new ShopNavigationState(home, value, detail, utilityRoot,
                    utilityChild, preview, receipt);
        }
        if (visible instanceof ShopRoute.Product && route instanceof ShopRoute.Product value) {
            return preview != null
                    ? new ShopNavigationState(home, discovery, detail, utilityRoot,
                            utilityChild, value, receipt)
                    : new ShopNavigationState(home, discovery, value, utilityRoot,
                            utilityChild, null, receipt);
        }
        if (visible instanceof ShopRoute.PaymentResult
                && route instanceof ShopRoute.PaymentResult value) {
            return new ShopNavigationState(home, discovery, detail, null, null, null, value);
        }
        throw new IllegalArgumentException("replacement must target the visible semantic slot");
    }

    ShopNavigationState captureVisible(ShopRoute route) {
        return replaceVisible(route);
    }

    ShopNavigationState back() {
        if (receipt != null) {
            return new ShopNavigationState(home, discovery, detail,
                    null, null, null, null);
        }
        if (preview != null) {
            return new ShopNavigationState(home, discovery, detail,
                    utilityRoot, utilityChild, null, null);
        }
        if (utilityChild != null) {
            return new ShopNavigationState(home, discovery, detail,
                    utilityRoot, null, null, null);
        }
        if (utilityRoot != null) {
            return new ShopNavigationState(home, discovery, detail,
                    null, null, null, null);
        }
        if (detail != null) {
            return new ShopNavigationState(home, discovery, null,
                    null, null, null, null);
        }
        if (discovery != null) return reset(homeOrDefault());
        return this;
    }

    ShopNavigationState reset(ShopRoute.Home route) {
        return new ShopNavigationState(Objects.requireNonNull(route, "route"),
                null, null, null, null, null, null);
    }

    ShopNavigationState openFromRoot(ShopRoute.Home root, ShopRoute target) {
        return reset(root).open(target);
    }

    ShopNavigationState completeCheckout(ShopRoute.PaymentResult result) {
        return new ShopNavigationState(homeOrDefault(), discovery, detail,
                null, null, null, Objects.requireNonNull(result, "result"));
    }

    boolean canGoBack() {
        return receipt != null || preview != null || utilityChild != null
                || utilityRoot != null || detail != null || discovery != null;
    }

    List<ShopRoute> backTargets() {
        Deque<ShopRoute> targets = new ArrayDeque<>();
        ShopNavigationState cursor = this;
        while (cursor.canGoBack()) {
            cursor = cursor.back();
            targets.addFirst(cursor.current().orElseThrow());
        }
        return List.copyOf(targets);
    }

    int nodeCount() {
        return count(home) + count(discovery) + count(detail) + count(utilityRoot)
                + count(utilityChild) + count(preview) + count(receipt);
    }

    private ShopNavigationState openDiscovery(ShopRoute route) {
        return new ShopNavigationState(homeOrDefault(), route, null,
                null, null, null, null);
    }

    private ShopNavigationState openProduct(ShopRoute.Product route) {
        if (utilityRoot instanceof ShopRoute.Cart) {
            return new ShopNavigationState(homeOrDefault(), discovery, detail,
                    utilityRoot, utilityChild, route, null);
        }
        return new ShopNavigationState(homeOrDefault(), discovery, route,
                null, null, null, null);
    }

    private ShopNavigationState openUtility(ShopRoute route) {
        return new ShopNavigationState(homeOrDefault(), discovery, detail,
                route, null, null, null);
    }

    private ShopNavigationState openMyChild(ShopRoute route) {
        return new ShopNavigationState(homeOrDefault(), discovery, detail,
                new ShopRoute.My(), route, null, null);
    }

    private ShopNavigationState openCheckout(ShopRoute.Checkout route) {
        return new ShopNavigationState(homeOrDefault(), discovery, detail,
                new ShopRoute.Cart(), route, null, null);
    }

    private ShopRoute.Home homeOrDefault() {
        return home == null ? ShopRoute.defaultHome() : home;
    }

    private static int count(Object value) {
        return value == null ? 0 : 1;
    }
}
```

Add imports for `ArrayDeque`, `Deque`, `List`, `Objects`, and `Optional`. Verify the implementation against these semantic rules:

- `current()` priority is receipt, preview, utility child, utility root, detail, discovery, home.
- Opening `Home` resets to that Home only.
- Opening `Search` or `Storefront` replaces discovery, clears detail and every overlay/receipt slot.
- Opening `Product` without a Cart overlay replaces detail and clears overlays/receipt.
- `back()` removes one highest slot and never removes Home.
- The constructor validates that discovery is null, `Search`, or `Storefront`; utility root is null, `My`, or `Cart`; and utility child is compatible with its root.

Use private copy helpers such as `withContent(...)` only inside this class. Do not expose setters or mutable collections.

- [ ] **Step 5: Run the content tests green**

Import `ProductSearchQuery` and `ProductSortMode`, then run:

```powershell
mvn -pl vcampus-client -am '-Dtest=ShopNavigationStateTest' test
```

Expected: the three content/default-Home tests pass.

- [ ] **Step 6: Add failing overlay, checkout, receipt, and long-cycle tests**

Add:

```java
@Test
void cartAndCheckoutProductPreviewsReturnToTheirOwner() {
    ShopNavigationState cart = ShopNavigationState.empty()
            .open(ShopRoute.defaultHome())
            .open(new ShopRoute.Cart())
            .open(new ShopRoute.Product("cart-product"));
    assertThat(cart.back().current()).contains(new ShopRoute.Cart());

    ShopNavigationState checkout = cart.back()
            .open(new ShopRoute.Checkout())
            .open(new ShopRoute.Product("checkout-product"));
    assertThat(checkout.back().current()).contains(new ShopRoute.Checkout());
    assertThat(checkout.back().back().current()).contains(new ShopRoute.Cart());
}

@Test
void utilitySwitchingReplacesOneRootAndPreservesContentAnchor() {
    ShopNavigationState state = ShopNavigationState.empty()
            .open(ShopRoute.defaultHome())
            .open(new ShopRoute.Storefront("shop-1"))
            .open(new ShopRoute.Product("p-1"))
            .open(new ShopRoute.My())
            .open(new ShopRoute.Cart())
            .open(new ShopRoute.My());

    assertThat(state.back().current()).contains(new ShopRoute.Product("p-1"));
    assertThat(state.nodeCount()).isLessThanOrEqualTo(4);
}

@Test
void userReportedCycleIsBoundedAndAlwaysReturnsHome() {
    ShopNavigationState state = ShopNavigationState.empty().open(ShopRoute.defaultHome());
    for (int index = 0; index < 100; index++) {
        state = state.open(new ShopRoute.Product("home-" + index));
        state = state.open(new ShopRoute.Cart());
        state = state.open(new ShopRoute.Product("cart-" + index));
        state = state.open(new ShopRoute.Storefront("shop-" + index));
        state = state.open(new ShopRoute.Product("store-" + index));
        state = state.open(new ShopRoute.Cart());
    }

    assertThat(state.nodeCount()).isLessThanOrEqualTo(4);
    for (int index = 0; index < 4 && state.canGoBack(); index++) state = state.back();
    assertThat(state.current()).contains(ShopRoute.defaultHome());
    assertThat(state.canGoBack()).isFalse();
}

@Test
void completedPaymentClearsCheckoutFromEveryBackTarget() {
    ShopRoute.PaymentResult receipt = new ShopRoute.PaymentResult(payment());
    ShopNavigationState state = ShopNavigationState.empty()
            .open(ShopRoute.defaultHome())
            .open(new ShopRoute.Cart())
            .open(new ShopRoute.Checkout())
            .completeCheckout(receipt);

    assertThat(state.current()).contains(receipt);
    assertThat(state.backTargets()).noneMatch(ShopRoute.Checkout.class::isInstance);
    assertThat(state.back().current()).contains(ShopRoute.defaultHome());
}
```

Define this exact fixture in the test:

```java
private static PaymentView payment() {
    return new PaymentView("payment-1", "group-1", "P0001",
            new BigDecimal("12.00"), PaymentStatus.SUCCEEDED,
            PaymentChannel.ALIPAY, Instant.parse("2026-09-01T00:00:00Z"),
            null, 0);
}
```

Import `BigDecimal`, `Instant`, `PaymentChannel`, `PaymentStatus`, and `PaymentView`.

- [ ] **Step 7: Run the new tests red, then implement the remaining transition rules**

Run the focused command from Step 5. Expected red assertions before implementation:

- Cart product currently occupies content detail instead of preview.
- utility switching retains or appends the wrong nodes.
- the long cycle exceeds four nodes or cannot reach Home.
- completed receipt retains Checkout.

Implement these exact rules:

- Opening `My` or `Cart` replaces `utilityRoot` and clears utility child, preview, and receipt while leaving content untouched.
- Opening `SellerApplication`, `SellerWorkspace`, or `AdminWorkspace` sets `My` as root, replaces utility child, and clears preview/receipt.
- Opening `Checkout` sets `Cart` as root and Checkout as child, clearing preview/receipt.
- Opening `Product` while Cart is the utility root places/replaces preview and preserves Cart/Checkout below it.
- Opening Search/Storefront from a preview exits the complete overlay path, replaces discovery, and clears detail.
- Opening `PaymentResult` delegates to `completeCheckout`.
- Back priority is receipt → preview → utility child → utility root → detail → discovery → Home.

Run the focused command again. Expected: all `ShopNavigationStateTest` tests pass.

- [ ] **Step 8: Commit the pure state engine**

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopRoute.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigationState.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigationStateTest.java
git commit -m "feat(shop): add layered navigation state"
```

### Task 2: ShopNavigator Delegation, Capture, and Guard Semantics

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigator.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigatorTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`

**Interfaces:**
- Consumes: all `ShopNavigationState` operations from Task 1 and existing `ShopRouteHost.capture/render`.
- Produces: queue-free `ShopNavigator`; `resetToDefaultHome()`; derived diagnostic `history()`; unchanged listener and leave-guard public seams.

- [ ] **Step 1: Replace the bounded-history test with failing semantic tests**

Delete `republishesCurrentRouteAndBoundsRestorableHistory()` and add:

```java
@Test
void repeatedProductsReplaceOneDetailLayerAndKeepHomeReachable() {
    ShopNavigator navigator = new ShopNavigator(route -> { });
    navigator.open(ShopRoute.defaultHome());
    IntStream.range(0, 100).forEach(index ->
            navigator.open(new ShopRoute.Product("product-" + index)));

    assertThat(navigator.history()).containsExactly(ShopRoute.defaultHome());
    navigator.back();
    assertThat(navigator.current()).contains(ShopRoute.defaultHome());
    assertThat(navigator.canGoBack()).isFalse();
}

@Test
void resetToDefaultHomeWaitsForTheActiveLeaveGuard() {
    AtomicReference<Runnable> held = new AtomicReference<>();
    ShopNavigator navigator = new ShopNavigator(route -> { });
    navigator.open(ShopRoute.defaultHome());
    navigator.open(new ShopRoute.SellerApplication());
    navigator.setLeaveGuard(held::set);

    navigator.resetToDefaultHome();

    assertThat(navigator.current()).contains(new ShopRoute.SellerApplication());
    held.get().run();
    assertThat(navigator.current()).contains(ShopRoute.defaultHome());
    assertThat(navigator.history()).isEmpty();
}
```

Update existing assertions that expected individual products in history. Preserve tests for same-route live capture, listener ordering, stale leave callbacks, and complete query identity.

- [ ] **Step 2: Run `ShopNavigatorTest` and confirm the serial model fails**

```powershell
mvn -pl vcampus-client -am '-Dtest=ShopNavigatorTest' test
```

Expected: the 100-product assertion still sees a bounded list of recent products, and `resetToDefaultHome()` is missing.

- [ ] **Step 3: Replace deque fields with one state field**

In `ShopNavigator`, remove `MAX_HISTORY`, `Deque<ShopRoute> history`, `ShopRoute current`, and `utilityAnchor`. Add:

```java
private ShopNavigationState state = ShopNavigationState.empty();

public Optional<ShopRoute> current() { return state.current(); }
public List<ShopRoute> history() { return state.backTargets(); }
public boolean canGoBack() { return state.canGoBack(); }
```

Use one helper for state capture:

```java
private ShopNavigationState captureCurrent() {
    return state.current()
            .map(route -> state.captureVisible(
                    Objects.requireNonNull(host.capture(route), "captured route")))
            .orElse(state);
}
```

Implement transitions without mutating state until the leave guard proceeds:

```java
public void open(ShopRoute route) {
    Objects.requireNonNull(route, "route");
    if (state.current().filter(route::equals).isPresent()) {
        state = captureCurrent();
        publish();
        return;
    }
    requestTransition(() -> {
        state = captureCurrent().open(route);
        publish();
    });
}

public void back() {
    if (!state.canGoBack()) return;
    requestTransition(() -> {
        state = captureCurrent().back();
        publish();
    });
}

public void replaceCurrent(ShopRoute route) {
    state = state.replaceVisible(Objects.requireNonNull(route, "route"));
    publish();
}

public void resetToDefaultHome() {
    reset(ShopRoute.defaultHome());
}

public void reset(ShopRoute.Home home) {
    requestTransition(() -> {
        state = state.reset(Objects.requireNonNull(home, "home"));
        publish();
    });
}

public void openFromRoot(ShopRoute.Home root, ShopRoute target) {
    requestTransition(() -> {
        state = state.openFromRoot(root, target);
        publish();
    });
}

public void completeCheckout(ShopRoute.PaymentResult receipt) {
    state = captureCurrent().completeCheckout(receipt);
    publish();
}
```

Publish from the state rather than a removed `current` field:

```java
private void publish() {
    ShopRoute route = state.current().orElseThrow();
    host.render(route);
    List.copyOf(listeners).forEach(listener -> listener.accept(route));
}

public void renderCurrent() {
    if (state.current().isPresent()) publish();
}
```

Keep the existing `transitionVersion` comparison exactly in `requestTransition`. `completeCheckout` remains an authoritative asynchronous terminal transition, matching the approved design and existing accepted-payment behavior.

- [ ] **Step 4: Migrate tests that reset directly to non-Home routes**

Replace test setup such as:

```java
navigator.reset(search);
```

with:

```java
navigator.openFromRoot(ShopRoute.defaultHome(), search);
```

Only production Home reset remains `reset(ShopRoute.Home)`. Do not add an untyped `reset(ShopRoute)` overload merely to keep old tests compiling.

- [ ] **Step 5: Run focused navigation and coordinator tests green**

```powershell
mvn -pl vcampus-client -am '-Dtest=ShopNavigationStateTest,ShopNavigatorTest,ShopUiTest' test
```

Expected: all selected tests pass; `history()` assertions now describe only current restorable semantic layers; no assertion expects a list of repeated products.

- [ ] **Step 6: Commit navigator delegation**

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigator.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigatorTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java
git commit -m "refactor(shop): use semantic navigation layers"
```

### Task 3: Buyer Preview and Payment Navigation Migration

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/PaymentResultPanel.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/SimulatedCashierDialog.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/PurchasePanelsTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/CatalogPaginationTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`

**Interfaces:**
- Consumes: contextual `ShopNavigationState.open(Product)`, `ShopNavigator.completeCheckout(...)`, `resetToDefaultHome()`, and `openFromRoot(Home, target)`.
- Produces: Cart/Checkout product preview return behavior; terminal payments with no Checkout back target; canonical receipt exits.

- [ ] **Step 1: Add failing panel-level preview tests**

Add to `PurchasePanelsTest`:

```java
@Test
void cartProductPreviewReturnsToCartAndCheckoutPreviewReturnsToCheckout() throws Exception {
    ShopNavigator navigator = new ShopNavigator(route -> { });
    navigator.open(ShopRoute.defaultHome());
    navigator.open(new ShopRoute.Cart());
    navigator.open(new ShopRoute.Product("cart-product"));
    navigator.back();
    assertThat(navigator.current()).contains(new ShopRoute.Cart());

    navigator.open(new ShopRoute.Checkout());
    navigator.open(new ShopRoute.Product("checkout-product"));
    navigator.back();
    assertThat(navigator.current()).contains(new ShopRoute.Checkout());
}
```

Extend the accepted-payment test with:

```java
assertThat(navigator.history()).noneMatch(ShopRoute.Checkout.class::isInstance);
navigator.back();
assertThat(navigator.current()).contains(ShopRoute.defaultHome());
```

- [ ] **Step 2: Run buyer tests and observe any remaining red integration behavior**

```powershell
mvn -pl vcampus-client -am '-Dtest=PurchasePanelsTest,CatalogPaginationTest,ShopUiTest' test
```

Expected before migration: compilation or assertions fail where `PaymentResultPanel` calls the old untyped `reset`, where cashier completion uses `replaceCurrent`, and where pagination fixtures reset directly to Search/Storefront.

- [ ] **Step 3: Use terminal and canonical Home APIs**

In both default constructors of `SimulatedCashierDialog`, replace:

```java
payment -> navigator.replaceCurrent(new ShopRoute.PaymentResult(payment))
```

with:

```java
payment -> navigator.completeCheckout(new ShopRoute.PaymentResult(payment))
```

In `PaymentResultPanel`:

```java
public void openHome() {
    navigator.resetToDefaultHome();
}

public void openPaidOrders() {
    navigator.openFromRoot(ShopRoute.defaultHome(), new ShopRoute.My());
}
```

Remove now-unused `HomeProductQuery` and `ProductSortMode` imports.

- [ ] **Step 4: Migrate pagination fixtures and verify captured state restoration**

Use `openFromRoot(ShopRoute.defaultHome(), route)` for Search/Storefront fixture setup. Preserve assertions that returned Search/Storefront routes retain the exact query, page, and scroll state captured before Product opens.

Run the Step 2 command again. Expected: all selected tests pass; Cart preview returns Cart, Checkout preview returns Checkout, and terminal receipt history contains no Checkout.

- [ ] **Step 5: Commit buyer navigation migration**

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/PaymentResultPanel.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/buyer/SimulatedCashierDialog.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/PurchasePanelsTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/buyer/CatalogPaginationTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java
git commit -m "fix(shop): preserve buyer flow return layers"
```

### Task 4: Shared Forced-Home Action in Toolbar and Sidebar

**Files:**
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopToolbar.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java`
- Modify: `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopUiInstaller.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java`
- Modify: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/SellerApplicationPanelTest.java`

**Interfaces:**
- Consumes: `ShopNavigator.resetToDefaultHome()` and the existing seller-application leave guard.
- Produces: `ShopPageCoordinator.goHome()`; `InstalledCoordinator.goHome()`; `ShopToolbar(..., Runnable returnHome)`; fixed button `shop.return-home`.

- [ ] **Step 1: Add failing toolbar order and equivalence tests**

Update `toolbarReflectsEveryRouteAndUsesTheAuthoritativeQuantitySum()` to construct the toolbar with a callback and require this action order:

```java
AtomicInteger homeRequests = new AtomicInteger();
ShopToolbar toolbar = onEdt(() -> new ShopToolbar(
        navigator, cartCount, new DefaultShopUiKit(), homeRequests::incrementAndGet));

assertThat(Arrays.stream(component(toolbar, "shop.actions", JPanel.class)
        .getComponents()).map(Component::getName).toList())
        .containsExactly("shop.return-home", "shop.my", "shop.cart");
```

For every route in the existing route loop, assert:

```java
assertThat(component(toolbar, "shop.return-home", JButton.class).isVisible()).isTrue();
```

Click it and require one callback invocation.

Add a coordinator test:

```java
@Test
void goHomeResetsEveryLayerToFirstPageTopAndCannotReturn() throws Exception {
    ShopModulePanel content = onEdt(ShopModulePanel::new);
    ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
            content, buyer(), new RecordingClient(), new DefaultShopUiKit(), () -> { }));
    onEdt(() -> {
        coordinator.navigator().open(ShopRoute.defaultHome());
        coordinator.navigator().open(new ShopRoute.Storefront("shop-1"));
        coordinator.navigator().open(new ShopRoute.Product("product-1"));
        coordinator.navigator().open(new ShopRoute.Cart());
        coordinator.goHome();
    });

    assertThat(coordinator.navigator().current()).contains(ShopRoute.defaultHome());
    assertThat(coordinator.navigator().history()).isEmpty();
    assertThat(coordinator.navigator().canGoBack()).isFalse();
    assertVisible(content, "shop.home");
}
```

- [ ] **Step 2: Add a failing installer test for sidebar equivalence**

Rename `originalShopEntryCallsEnterExactlyOncePerClick()` to `originalShopEntryCallsGoHomeExactlyOncePerClick()` and change the recording coordinator assertion to:

```java
onEdt(shop::doClick);
assertThat(coordinator.homeRequests).hasValue(1);
assertThat(coordinator.entries).hasValue(0);
```

Update the double-click integration test to expect two default Home loads, because each sidebar click is now an explicit reset rather than a passive re-entry.

- [ ] **Step 3: Run the focused UI tests red**

```powershell
mvn -pl vcampus-client -am '-Dtest=ShopUiTest,SellerApplicationPanelTest' test
```

Expected: `ShopToolbar` lacks the callback/button constructor, `ShopPageCoordinator.goHome()` is missing, and `ShopUiInstaller` still calls `enter()`.

- [ ] **Step 4: Implement one shared coordinator action**

Add to `ShopPageCoordinator`:

```java
public void goHome() {
    requireEdt();
    if (disposed) return;
    pages.syncCartCount();
    navigator.resetToDefaultHome();
}
```

Install the toolbar with the same method reference:

```java
cards.installToolbar(new ShopToolbar(navigator, cartCount, uiKit, this::goHome));
```

Keep `enter()` only for internal initial-card compatibility if a remaining caller needs it; it must not be wired to the sidebar.

- [ ] **Step 5: Add the fixed toolbar button**

Change the constructor signature to:

```java
public ShopToolbar(ShopNavigator navigator, CartCountModel cartCount,
        ShopUiKit uiKit, Runnable returnHome)
```

Create and install the button:

```java
JButton home = uiKit.secondaryButton("shop.return-home", "返回首页");
actions.add(home);
actions.add(my);
actions.add(cart);
home.addActionListener(event -> returnHome.run());
```

`refresh()` never hides `home`. Update all direct test constructors to pass either `navigator::resetToDefaultHome` or a recording callback.

- [ ] **Step 6: Wire the shell entry to the same action**

Add `void goHome()` to `ShopUiInstaller.InstalledCoordinator`, implement it through `ShopPageCoordinator`, and change:

```java
entry.addActionListener(event -> coordinator.goHome());
```

Update `RecordingInstalledCoordinator` with `AtomicInteger homeRequests` and a `goHome()` implementation. Do not add a second reset implementation inside the installer.

- [ ] **Step 7: Verify unsaved application protection for both entry points**

Add a coordinator fixture whose `requestSellerApplicationLeave(Runnable proceed)` stores the callback. Exercise `coordinator.goHome()` and the toolbar button separately:

```java
onEdt(() -> coordinator.navigator().open(new ShopRoute.SellerApplication()));
onEdt(coordinator::goHome);
assertThat(coordinator.navigator().current())
        .contains(new ShopRoute.SellerApplication());
onEdt(heldProceed.get());
assertThat(coordinator.navigator().current()).contains(ShopRoute.defaultHome());
```

Retain the existing real `SellerApplicationPanelTest` cases for save, discard, cancel, and failed save. The new test proves both entry points pass through the same navigator guard; it must not simulate button-specific draft logic.

- [ ] **Step 8: Run focused tests green and commit**

```powershell
mvn -pl vcampus-client -am '-Dtest=ShopNavigationStateTest,ShopNavigatorTest,ShopUiTest,SellerApplicationPanelTest' test
```

Expected: all selected tests pass; `shop.return-home` is first in `shop.actions`, both entry points reset to default Home, and guard cancellation leaves navigation unchanged.

```powershell
git add -- vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopToolbar.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopPageCoordinator.java vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/ShopUiInstaller.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/ShopUiTest.java vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/seller/SellerApplicationPanelTest.java
git commit -m "feat(shop): add fixed return-home actions"
```

### Task 5: Full Regression, Demo Evidence, and Documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-09-01-vcampus-shop-manual-test-findings.md`
- Modify: `docs/superpowers/specs/2026-09-02-vcampus-shop-layered-navigation-design.md`
- Modify only if assertions require it: `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/demo/ShopAuthDemoClientMainTest.java`

**Interfaces:**
- Consumes: completed Tasks 1–4.
- Produces: complete automated evidence for `SHOP-TEST-008` and `SHOP-TEST-011`, plus an updated manual-test checklist. No push or integration action.

- [ ] **Step 1: Run every Shop client test before the full repository build**

```powershell
mvn -pl vcampus-client -am test
```

Expected: Common, Server, and Client tests all pass with zero failures and zero errors. Record the exact test counts from Maven output; do not reuse counts from an earlier commit.

- [ ] **Step 2: Run complete repository verification**

```powershell
mvn verify
git diff --check
```

Expected: every module passes and the working changes contain no whitespace errors.

- [ ] **Step 3: Verify Demo launch/package scripts are unchanged and still valid**

```powershell
& .\vcampus-distribution\scripts\tests\start-shop-auth-demo-scripts.tests.ps1
& .\vcampus-distribution\scripts\tests\build-shop-auth-demo-package.tests.ps1
```

Expected: both PowerShell suites pass; default server port remains 8888. These tests do not authorize starting or stopping unrelated local Java processes.

- [ ] **Step 4: Update findings and design status with exact evidence**

In the findings table, change `SHOP-TEST-011` only to “自动化已整改，待人工复测” and include:

- the 5/10/100-cycle test names;
- the fixed maximum semantic node count;
- Cart and Checkout preview return tests;
- sidebar and toolbar forced-home tests;
- exact focused and full Maven counts.

Change the design status from `待用户评审` to `已实现，待人工验收`. Do not mark the issue fully solved until the user completes the real Demo path.

- [ ] **Step 5: Commit documentation and any necessary Demo assertion update**

```powershell
git add -- docs/superpowers/specs/2026-09-01-vcampus-shop-manual-test-findings.md docs/superpowers/specs/2026-09-02-vcampus-shop-layered-navigation-design.md
git commit -m "docs(shop): record layered navigation evidence"
```

If `ShopAuthDemoClientMainTest.java` required an assertion update, stage it explicitly in the same commit only when it documents the new fixed toolbar button; otherwise leave it untouched.

- [ ] **Step 6: Prepare the manual acceptance sequence**

Run the Demo with one server on port 8888 and one or more clients. Verify without changing server data outside the normal Shop test flow:

1. Home → product → Cart → product → storefront → product → Cart, repeated 10 times.
2. Press Back until Home; confirm the number of presses depends only on the final layers and Home is reached.
3. From Cart open a product and press Back; confirm Cart.
4. From Checkout open a product and press Back; confirm Checkout.
5. From a deep route click toolbar “返回首页”; confirm Home page 1 at top and Back disabled.
6. Repeat from a deep route using sidebar “校园商城”; confirm identical result.
7. Edit an unsaved seller application, try both Home entries, and verify save/discard/cancel behavior.
8. Complete a payment and verify Back never reopens Checkout; verify “继续购物” and “查看已支付订单”.

Record screenshots and user confirmation in the findings document in a later user-approved documentation update. Do not claim manual success from automated tests.

## Self-Review Record

- **Spec coverage:** Tasks 1–2 cover fixed content/overlay/receipt slots, same-layer replacement, bounded cycles, derived Back, route state capture, and stale guards. Task 3 covers Cart/Checkout preview and payment terminal rules. Task 4 covers both forced-home entry points and their fixed layout/guard semantics. Task 5 covers full regression and manual acceptance.
- **Placeholder scan:** No `TBD`, `TODO`, “implement later”, comment-only method body, or unspecified error-handling step remains.
- **Type consistency:** The plan consistently uses `ShopRoute.defaultHome()`, `ShopNavigationState.replaceVisible(...)`, `ShopNavigator.resetToDefaultHome()`, `ShopPageCoordinator.goHome()`, `InstalledCoordinator.goHome()`, and component name `shop.return-home`.
- **Scope check:** All production changes remain inside `vcampus-client/.../shop`; no database, server, User Manager, or public network change is required.
