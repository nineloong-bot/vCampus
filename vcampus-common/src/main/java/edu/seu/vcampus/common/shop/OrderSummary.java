package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummary(String orderId, String orderGroupId, String orderNumber,
        String shopId, String shopName, BigDecimal orderAmount,
        OrderStatus status, Instant createdAt) implements Serializable { }
