package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Carries immutable paid order view data.
 * @param orderId the order identifier
 * @param orderNumber the order number
 * @param shopId the shop identifier
 * @param shopName the shop name
 * @param totalAmount the total amount
 * @param paidAt the paid at
 * @param status the status
 * @param items the items
 */
public record PaidOrderView(String orderId, String orderNumber, String shopId,
        String shopName, BigDecimal totalAmount, Instant paidAt, OrderStatus status,
        List<PaidOrderItemView> items) implements Serializable {
    /**
     * Validates and creates a paid order view.
     * @param orderId the order id
     * @param orderNumber the order number
     * @param shopId the shop id
     * @param shopName the shop name
     * @param totalAmount the total amount
     * @param paidAt the paid at
     * @param status the status
     * @param items the items
     */
    public PaidOrderView {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderNumber, "orderNumber");
        Objects.requireNonNull(shopId, "shopId");
        Objects.requireNonNull(shopName, "shopName");
        Objects.requireNonNull(totalAmount, "totalAmount");
        Objects.requireNonNull(paidAt, "paidAt");
        Objects.requireNonNull(status, "status");
        items = List.copyOf(items);
        if (totalAmount.signum() < 0) {
            throw new IllegalArgumentException("totalAmount must be non-negative");
        }
        if (status != OrderStatus.PAID) {
            throw new IllegalArgumentException("status must be PAID");
        }
        BigDecimal expectedTotal = items.stream()
                .map(PaidOrderItemView::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAmount.compareTo(expectedTotal) != 0) {
            throw new IllegalArgumentException("totalAmount must equal the sum of item lineAmount values");
        }
    }
}
