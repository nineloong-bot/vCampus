package edu.seu.vcampus.client.shop.ui.navigation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Maintains bounded buyer route history and delegates page rendering. */
public final class ShopNavigator {
    private static final int MAX_HISTORY = 20;

    private final ShopRouteHost host;
    private final Deque<ShopRoute> history = new ArrayDeque<>();
    private final List<Consumer<ShopRoute>> listeners = new ArrayList<>();
    private ShopRoute current;
    private ShopRoute utilityAnchor;
    private ShopLeaveGuard leaveGuard = ShopLeaveGuard.immediate();
    private long transitionVersion;

    public ShopNavigator(ShopRouteHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public void open(ShopRoute route) {
        Objects.requireNonNull(route, "route");
        if (current != null) {
            boolean sameRoute = route.equals(current);
            if (sameRoute) {
                current = Objects.requireNonNull(host.capture(current), "captured route");
                publish();
                return;
            }
        }
        requestTransition(() -> openNow(route));
    }

    public void back() {
        if (isUtility(current) && utilityAnchor != null) {
            requestTransition(() -> {
                if (!history.isEmpty() && history.peekLast().equals(utilityAnchor)) {
                    history.removeLast();
                }
                current = utilityAnchor;
                utilityAnchor = null;
                publish();
            });
            return;
        }
        if (history.isEmpty()) {
            return;
        }
        requestTransition(() -> {
            current = history.removeLast();
            publish();
        });
    }

    public boolean canGoBack() {
        return (isUtility(current) && utilityAnchor != null) || !history.isEmpty();
    }

    public void setLeaveGuard(ShopLeaveGuard leaveGuard) {
        this.leaveGuard = Objects.requireNonNull(leaveGuard, "leaveGuard");
    }

    public void addListener(Consumer<ShopRoute> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void replaceCurrent(ShopRoute route) {
        current = Objects.requireNonNull(route, "route");
        publish();
    }

    public void reset(ShopRoute route) {
        history.clear();
        utilityAnchor = null;
        current = Objects.requireNonNull(route, "route");
        publish();
    }

    /** Opens a target with one explicit safe back destination in a single state change. */
    public void openFromRoot(ShopRoute root, ShopRoute target) {
        ShopRoute safeRoot = Objects.requireNonNull(root, "root");
        ShopRoute destination = Objects.requireNonNull(target, "target");
        history.clear();
        utilityAnchor = isUtility(destination) ? safeRoot : null;
        addHistory(safeRoot);
        current = destination;
        publish();
    }

    /** Publishes a receipt while removing every completed checkout route from history. */
    public void completeCheckout(ShopRoute.PaymentResult receipt) {
        Objects.requireNonNull(receipt, "receipt");
        ShopRoute captured = current == null
                ? null
                : Objects.requireNonNull(host.capture(current), "captured route");
        history.removeIf(ShopRoute.Checkout.class::isInstance);
        if (captured != null && !(captured instanceof ShopRoute.Checkout)
                && !captured.equals(receipt)) {
            addHistory(captured);
        }
        current = receipt;
        publish();
    }

    public void renderCurrent() {
        if (current != null) publish();
    }

    public Optional<ShopRoute> current() {
        return Optional.ofNullable(current);
    }

    public List<ShopRoute> history() {
        return List.copyOf(history);
    }

    private void publish() {
        host.render(current);
        List.copyOf(listeners).forEach(listener -> listener.accept(current));
    }

    private void openNow(ShopRoute route) {
        if (current != null) {
            ShopRoute captured = Objects.requireNonNull(host.capture(current), "captured route");
            if (isUtility(route)) {
                if (!isUtility(captured)) {
                    utilityAnchor = captured;
                    addHistory(captured);
                }
            } else {
                addHistory(captured);
            }
        }
        current = route;
        publish();
    }

    private void requestTransition(Runnable transition) {
        long requestedVersion = ++transitionVersion;
        ShopLeaveGuard requestedGuard = leaveGuard;
        requestedGuard.requestLeave(() -> {
            if (requestedVersion != transitionVersion) {
                return;
            }
            leaveGuard = ShopLeaveGuard.immediate();
            transition.run();
        });
    }

    private static boolean isUtility(ShopRoute route) {
        return route instanceof ShopRoute.My || route instanceof ShopRoute.Cart;
    }

    private void addHistory(ShopRoute route) {
        history.addLast(route);
        if (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
    }
}
