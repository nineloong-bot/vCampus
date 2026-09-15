package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Carries immutable cart view data.
 * @param cartId the cart identifier
 * @param items the items
 * @param displayedTotal the displayed total
 */
public record CartView(String cartId, List<CartItemView> items,
        BigDecimal displayedTotal) implements Serializable {
    /**
 * Validates and creates a cart view.
 * @param cartId the cart identifier
 * @param items the items
 * @param displayedTotal the displayed total
 */
public CartView { items = List.copyOf(items); }
}
