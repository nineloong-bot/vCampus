package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Carries immutable create sku command data.
 * @param skuName the sku name
 * @param unitPrice the unit price
 * @param stockQuantity the stock quantity
 * @param active the active
 */
public record CreateSkuCommand(String skuName, BigDecimal unitPrice,
        long stockQuantity, boolean active) implements Serializable { }
