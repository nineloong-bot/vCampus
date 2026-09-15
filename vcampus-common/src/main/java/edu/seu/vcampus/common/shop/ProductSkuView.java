package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Carries immutable product sku view data.
 * @param skuId the sku identifier
 * @param skuName the sku name
 * @param unitPrice the unit price
 * @param availableQuantity the available quantity
 * @param stockQuantity the stock quantity
 * @param reservedQuantity the reserved quantity
 * @param active the active
 * @param rowVersion the row version
 */
public record ProductSkuView(String skuId, String skuName, BigDecimal unitPrice,
        long availableQuantity, long stockQuantity, long reservedQuantity,
        boolean active, long rowVersion) implements Serializable {

    /**
     * Validates and creates a product sku view.
     * @param skuId the sku identifier
     * @param skuName the sku name
     * @param unitPrice the unit price
     * @param availableQuantity the available quantity
     * @param active the active
     * @param rowVersion the row version
     */
    public ProductSkuView(String skuId, String skuName, BigDecimal unitPrice,
            long availableQuantity, boolean active, long rowVersion) {
        this(skuId, skuName, unitPrice, availableQuantity, availableQuantity, 0,
                active, rowVersion);
    }
}
