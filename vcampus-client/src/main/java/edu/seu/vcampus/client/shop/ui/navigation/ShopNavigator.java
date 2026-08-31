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

    public ShopNavigator(ShopRouteHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public void open(ShopRoute route) {
        Objects.requireNonNull(route, "route");
        if (current != null) {
            boolean sameRoute = route.equals(current);
            current = Objects.requireNonNull(host.capture(current), "captured route");
            if (sameRoute) {
                publish();
                return;
            }
            addHistory(current);
        }
        current = route;
        publish();
    }

    public void back() {
        if (history.isEmpty()) {
            return;
        }
        current = history.removeLast();
        publish();
    }

    public boolean canGoBack() { return !history.isEmpty(); }

    public void addListener(Consumer<ShopRoute> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void replaceCurrent(ShopRoute route) {
        current = Objects.requireNonNull(route, "route");
        publish();
    }

    public void reset(ShopRoute route) {
        history.clear();
        current = Objects.requireNonNull(route, "route");
        publish();
    }

    /** Opens a target with one explicit safe back destination in a single state change. */
    public void openFromRoot(ShopRoute root, ShopRoute target) {
        ShopRoute safeRoot = Objects.requireNonNull(root, "root");
        ShopRoute destination = Objects.requireNonNull(target, "target");
        history.clear();
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

    private void addHistory(ShopRoute route) {
        history.addLast(route);
        if (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
    }
}
