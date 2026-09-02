package edu.seu.vcampus.client.shop.ui.navigation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Owns semantic buyer navigation layers and delegates page rendering. */
public final class ShopNavigator {
    private final ShopRouteHost host;
    private final List<Consumer<ShopRoute>> listeners = new ArrayList<>();
    private ShopNavigationState state = ShopNavigationState.empty();
    private ShopLeaveGuard leaveGuard = ShopLeaveGuard.immediate();
    private long transitionVersion;

    public ShopNavigator(ShopRouteHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

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

    public boolean canGoBack() {
        return state.canGoBack();
    }

    public void setLeaveGuard(ShopLeaveGuard leaveGuard) {
        this.leaveGuard = Objects.requireNonNull(leaveGuard, "leaveGuard");
    }

    public void addListener(Consumer<ShopRoute> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void replaceCurrent(ShopRoute route) {
        state = state.replaceVisible(Objects.requireNonNull(route, "route"));
        publish();
    }

    public void resetToDefaultHome() {
        reset(ShopRoute.defaultHome());
    }

    public void reset(ShopRoute.Home route) {
        requestTransition(() -> {
            state = state.reset(Objects.requireNonNull(route, "route"));
            publish();
        });
    }

    /** Opens a target with one explicit safe back destination in a single state change. */
    public void openFromRoot(ShopRoute.Home root, ShopRoute target) {
        requestTransition(() -> {
            state = state.openFromRoot(Objects.requireNonNull(root, "root"),
                    Objects.requireNonNull(target, "target"));
            publish();
        });
    }

    /** Publishes a receipt while removing every completed checkout route from history. */
    public void completeCheckout(ShopRoute.PaymentResult receipt) {
        state = captureCurrent().completeCheckout(
                Objects.requireNonNull(receipt, "receipt"));
        publish();
    }

    public void renderCurrent() {
        if (state.current().isPresent()) publish();
    }

    public Optional<ShopRoute> current() {
        return state.current();
    }

    public List<ShopRoute> history() {
        return state.backTargets();
    }

    private void publish() {
        ShopRoute route = state.current().orElseThrow();
        host.render(route);
        List.copyOf(listeners).forEach(listener -> listener.accept(route));
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

    private ShopNavigationState captureCurrent() {
        return state.current()
                .map(route -> state.captureVisible(
                        Objects.requireNonNull(host.capture(route), "captured route")))
                .orElse(state);
    }
}
