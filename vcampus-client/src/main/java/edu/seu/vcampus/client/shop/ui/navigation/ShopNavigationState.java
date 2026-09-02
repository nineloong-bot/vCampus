package edu.seu.vcampus.client.shop.ui.navigation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable semantic content path for Shop navigation. */
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
        ShopRoute.Home root = home == null ? ShopRoute.defaultHome() : home;
        if (route instanceof ShopRoute.Home value) {
            return new ShopNavigationState(value, null, null, null, null, null, null);
        }
        if (route instanceof ShopRoute.Search || route instanceof ShopRoute.Storefront) {
            return new ShopNavigationState(root, route, null, null, null, null, null);
        }
        if (route instanceof ShopRoute.Product value) {
            if (utilityRoot instanceof ShopRoute.Cart) {
                return new ShopNavigationState(root, discovery, detail,
                        utilityRoot, utilityChild, value, null);
            }
            return new ShopNavigationState(root, discovery, value, null, null, null, null);
        }
        if (route instanceof ShopRoute.My || route instanceof ShopRoute.Cart) {
            return new ShopNavigationState(root, discovery, detail,
                    route, null, null, null);
        }
        if (route instanceof ShopRoute.SellerApplication
                || route instanceof ShopRoute.SellerWorkspace
                || route instanceof ShopRoute.AdminWorkspace) {
            return new ShopNavigationState(root, discovery, detail,
                    new ShopRoute.My(), route, null, null);
        }
        if (route instanceof ShopRoute.Checkout value) {
            return new ShopNavigationState(root, discovery, detail,
                    new ShopRoute.Cart(), value, null, null);
        }
        if (route instanceof ShopRoute.PaymentResult value) {
            return completeCheckout(value);
        }
        throw new IllegalArgumentException("unsupported route: " + route);
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
        if (discovery != null) {
            return new ShopNavigationState(home, null, null, null, null, null, null);
        }
        return this;
    }

    ShopNavigationState replaceVisible(ShopRoute route) {
        Objects.requireNonNull(route, "route");
        ShopRoute visible = current().orElseThrow();
        if (visible.equals(route)) return this;
        if (visible instanceof ShopRoute.Home && route instanceof ShopRoute.Home value) {
            return new ShopNavigationState(value, discovery, detail,
                    utilityRoot, utilityChild, preview, receipt);
        }
        if (visible instanceof ShopRoute.Search && route instanceof ShopRoute.Search value) {
            return new ShopNavigationState(home, value, detail,
                    utilityRoot, utilityChild, preview, receipt);
        }
        if (visible instanceof ShopRoute.Storefront
                && route instanceof ShopRoute.Storefront value) {
            return new ShopNavigationState(home, value, detail,
                    utilityRoot, utilityChild, preview, receipt);
        }
        if (visible instanceof ShopRoute.Product && route instanceof ShopRoute.Product value) {
            return preview != null
                    ? new ShopNavigationState(home, discovery, detail,
                            utilityRoot, utilityChild, value, receipt)
                    : new ShopNavigationState(home, discovery, value,
                            utilityRoot, utilityChild, null, receipt);
        }
        if (visible instanceof ShopRoute.PaymentResult
                && route instanceof ShopRoute.PaymentResult value) {
            return new ShopNavigationState(home, discovery, detail,
                    null, null, null, value);
        }
        throw new IllegalArgumentException(
                "replacement must target the visible semantic slot");
    }

    ShopNavigationState captureVisible(ShopRoute route) {
        return replaceVisible(route);
    }

    ShopNavigationState reset(ShopRoute.Home route) {
        return new ShopNavigationState(Objects.requireNonNull(route, "route"),
                null, null, null, null, null, null);
    }

    ShopNavigationState openFromRoot(ShopRoute.Home root, ShopRoute target) {
        return reset(root).open(target);
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

    ShopNavigationState completeCheckout(ShopRoute.PaymentResult result) {
        return new ShopNavigationState(home == null ? ShopRoute.defaultHome() : home,
                discovery, detail, null, null, null,
                Objects.requireNonNull(result, "result"));
    }

    private static int count(Object value) {
        return value == null ? 0 : 1;
    }
}
