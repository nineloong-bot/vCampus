package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Carries immutable product management summary data.
 * @param productId the product identifier
 * @param productName the product name
 * @param status the status
 * @param skuCount the sku count
 * @param minimumPrice the minimum price
 * @param totalStock the total stock
 * @param reservedStock the reserved stock
 * @param salesCount the sales count
 * @param rowVersion the row version
 */
public record ProductManagementSummary(String productId, String productName, ProductStatus status,
        long skuCount, BigDecimal minimumPrice, long totalStock, long reservedStock,
        long salesCount, long rowVersion) implements Serializable { }
