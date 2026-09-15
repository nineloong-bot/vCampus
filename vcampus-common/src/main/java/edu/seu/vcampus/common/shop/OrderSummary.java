package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Carries immutable order summary data.
 * @param orderId the order identifier
 * @param orderGroupId the order group identifier
 * @param orderNumber the order number
 * @param shopId the shop identifier
 * @param shopName the shop name
 * @param orderAmount the order amount
 * @param status the status
 * @param createdAt the created at
 */
public record OrderSummary(String orderId, String orderGroupId, String orderNumber,
        String shopId, String shopName, BigDecimal orderAmount,
        OrderStatus status, Instant createdAt) implements Serializable { }
