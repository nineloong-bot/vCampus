package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

/**
 * Carries immutable seller order history data.
 * @param orders the orders
 */
public record SellerOrderHistory(List<SellerOrderView> orders) implements Serializable {
    /**
 * Validates and creates a seller order history.
 * @param orders the orders
 */
public SellerOrderHistory { orders = List.copyOf(orders); }
}
