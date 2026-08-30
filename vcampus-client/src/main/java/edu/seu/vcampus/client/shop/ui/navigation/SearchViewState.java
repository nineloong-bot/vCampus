package edu.seu.vcampus.client.shop.ui.navigation;

import edu.seu.vcampus.common.shop.ProductSearchQuery;

import java.util.Objects;

/** Immutable search query, completion, filter visibility, and viewport position. */
public record SearchViewState(ProductSearchQuery query, boolean searched,
        boolean filtersExpanded, int scrollY) {
    public SearchViewState {
        Objects.requireNonNull(query, "query");
        if (scrollY < 0) throw new IllegalArgumentException("scrollY must not be negative");
    }

    public SearchViewState(ProductSearchQuery query, boolean filtersExpanded, int scrollY) {
        this(query, true, filtersExpanded, scrollY);
    }
}
