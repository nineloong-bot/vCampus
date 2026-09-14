package edu.seu.vcampus.common.shop.order;

import java.io.Serializable;
import java.util.List;

/** Immutable selected cart or direct-purchase lines. */
public record CheckoutRequest(List<OrderLine> lines, boolean fromCart) implements Serializable {
    /** Limits checkout size and rejects duplicate stable SKU identifiers. */
    public CheckoutRequest {
        if (lines == null || lines.isEmpty() || lines.size() > 100)
            throw new IllegalArgumentException("ORDER_INVALID_REQUEST");
        lines = List.copyOf(lines);
        if (lines.stream().map(OrderLine::skuId).distinct().count() != lines.size())
            throw new IllegalArgumentException("ORDER_INVALID_REQUEST");
    }
}
