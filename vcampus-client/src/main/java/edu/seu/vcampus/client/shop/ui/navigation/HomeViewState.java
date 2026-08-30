package edu.seu.vcampus.client.shop.ui.navigation;

import edu.seu.vcampus.common.shop.HomeProductQuery;

import java.util.Objects;

/** Immutable home catalog query and viewport position. */
public record HomeViewState(HomeProductQuery query, int scrollY) {
    public HomeViewState {
        Objects.requireNonNull(query, "query");
        if (scrollY < 0) throw new IllegalArgumentException("scrollY must not be negative");
    }
}
