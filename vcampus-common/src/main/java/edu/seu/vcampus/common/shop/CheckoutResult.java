package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Carries immutable checkout result data.
 * @param orderGroupId the order group identifier
 * @param paymentId the payment identifier
 * @param paymentNumber the payment number
 * @param totalAmount the total amount
 * @param expiresAt the expires at
 * @param orders the orders
 */
public record CheckoutResult(String orderGroupId, String paymentId,
        String paymentNumber, BigDecimal totalAmount, Instant expiresAt,
        List<OrderSummary> orders) implements Serializable {
    /**
     * Validates and creates a checkout result.
     * @param orderGroupId the order group identifier
     * @param paymentId the payment identifier
     * @param paymentNumber the payment number
     * @param totalAmount the total amount
     * @param expiresAt the expires at
     * @param orders the orders
     */
    public CheckoutResult {
        orders = List.copyOf(orders);
    }
}
