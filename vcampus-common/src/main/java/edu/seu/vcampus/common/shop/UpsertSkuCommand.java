package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Carries immutable upsert sku command data.
 * @param skuId the sku identifier
 * @param skuName the sku name
 * @param unitPrice the unit price
 * @param stockQuantity the stock quantity
 * @param active the active
 * @param expectedVersion the expected version
 */
public record UpsertSkuCommand(String skuId, String skuName, BigDecimal unitPrice,
        long stockQuantity, boolean active, long expectedVersion) implements Serializable { }
