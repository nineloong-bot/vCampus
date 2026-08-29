package edu.seu.vcampus.client.shop.ui.navigation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Maintains bounded buyer route history and delegates page rendering. */
public final class ShopNavigator {
    private static final int MAX_HISTORY = 20;

    private final ShopRouteHost host;
    private final Deque<ShopRoute> history = new ArrayDeque<>();
    private ShopRoute current;

    public ShopNavigator(ShopRouteHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public void open(ShopRoute route) {
        Objects.requireNonNull(route, "route");
        if (route.equals(current)) {
            return;
        }
        if (current != null) {
            history.addLast(current);
            if (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }
        current = route;
        host.render(route);
    }

    public void back() {
        if (history.isEmpty()) {
            return;
        }
        current = history.removeLast();
        host.render(current);
    }

    public Optional<ShopRoute> current() {
        return Optional.ofNullable(current);
    }

    public List<ShopRoute> history() {
        return List.copyOf(history);
    }
}
