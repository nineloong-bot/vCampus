package edu.seu.vcampus.client.shop.ui.navigation;

/** Rendering boundary for the fixed Shop page host. */
@FunctionalInterface
public interface ShopRouteHost {
    void render(ShopRoute route);
}
