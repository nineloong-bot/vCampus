package edu.seu.vcampus.server.shop.domain;

import java.math.BigDecimal;

/** Provides product sku behavior. */
public record ProductSku(String skuId, String productId, String skuName,
        BigDecimal unitPrice, long stockQuantity, long reservedQuantity,
        boolean active, long rowVersion) {
    /**
     * Performs the available quantity operation.
     * @return the operation result
     */
    public long availableQuantity() {
        return stockQuantity - reservedQuantity;
    }
}
