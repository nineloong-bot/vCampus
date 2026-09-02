package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CheckoutResult(String orderGroupId, String paymentId,
        String paymentNumber, BigDecimal totalAmount, Instant expiresAt,
        List<OrderSummary> orders) implements Serializable {
    public CheckoutResult {
        orders = List.copyOf(orders);
    }
}
