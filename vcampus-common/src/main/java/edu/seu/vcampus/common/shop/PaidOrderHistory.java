package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

/**
 * Carries immutable paid order history data.
 * @param orders the orders
 */
public record PaidOrderHistory(List<PaidOrderView> orders) implements Serializable {
    /**
     * Validates and creates a paid order history.
     * @param orders the orders
     */
    public PaidOrderHistory {
        orders = List.copyOf(orders);
    }
}
