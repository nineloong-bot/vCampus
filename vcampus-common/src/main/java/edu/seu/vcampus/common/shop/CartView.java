package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record CartView(String cartId, List<CartItemView> items,
        BigDecimal displayedTotal) implements Serializable {
    public CartView { items = List.copyOf(items); }
}
