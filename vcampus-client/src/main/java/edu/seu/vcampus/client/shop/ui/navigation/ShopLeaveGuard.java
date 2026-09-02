package edu.seu.vcampus.client.shop.ui.navigation;

/** Defers a route change until the current page permits leaving. */
@FunctionalInterface
public interface ShopLeaveGuard {
    void requestLeave(Runnable proceed);

    static ShopLeaveGuard immediate() {
        return Runnable::run;
    }
}
