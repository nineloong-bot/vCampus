package edu.seu.vcampus.common.shop.order;

import java.io.Serializable;
import java.math.BigDecimal;

/** Immutable historical purchase line, including permanently invalidated lines. */
public record OrderItem(String orderItemId, String skuId, String productName, String skuName,
                        BigDecimal unitPrice, int quantity, BigDecimal lineAmount, boolean valid)
        implements Serializable { }
