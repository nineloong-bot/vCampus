package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Carries immutable seller order view data.
 * @param orderId the order identifier
 * @param orderNumber the order number
 * @param buyerUserId the buyer user identifier
 * @param shopId the shop identifier
 * @param shopName the shop name
 * @param totalAmount the total amount
 * @param paidAt the paid at
 * @param status the status
 * @param items the items
 */
public record SellerOrderView(String orderId, String orderNumber, String buyerUserId,
        String shopId, String shopName, BigDecimal totalAmount, Instant paidAt,
        OrderStatus status, List<SellerOrderItemView> items) implements Serializable {
    /**
 * Validates and creates a seller order view.
 * @param orderId the order identifier
 * @param orderNumber the order number
 * @param buyerUserId the buyer user identifier
 * @param shopId the shop identifier
 * @param shopName the shop name
 * @param totalAmount the total amount
 * @param paidAt the paid at
 * @param status the status
 * @param items the items
 */
public SellerOrderView { items = List.copyOf(items); }
}
