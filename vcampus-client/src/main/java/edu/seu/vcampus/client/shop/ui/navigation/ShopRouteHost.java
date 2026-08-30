package edu.seu.vcampus.client.shop.ui.navigation;

/** Rendering boundary for the fixed Shop page host. */
@FunctionalInterface
public interface ShopRouteHost {
    void render(ShopRoute route);

    /** Captures transient view state before the route is placed in history. */
    default ShopRoute capture(ShopRoute route) { return route; }
}
