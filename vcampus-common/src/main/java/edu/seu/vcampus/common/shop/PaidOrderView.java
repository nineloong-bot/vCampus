package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PaidOrderView(String orderId, String orderNumber, String shopId,
        String shopName, BigDecimal totalAmount, Instant paidAt, OrderStatus status,
        List<PaidOrderItemView> items) implements Serializable {
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
    }
}
