package edu.seu.vcampus.client.shop.ui.navigation;

import edu.seu.vcampus.common.shop.ShopProductQuery;

import java.util.Objects;

/** Immutable storefront query and viewport position. */
public record StorefrontViewState(ShopProductQuery query, int scrollY) {
    public StorefrontViewState {
        Objects.requireNonNull(query, "query");
        if (scrollY < 0) throw new IllegalArgumentException("scrollY must not be negative");
    }
}
