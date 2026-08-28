package edu.seu.vcampus.server.shop.domain;

import java.math.BigDecimal;

public record ProductSku(String skuId, String productId, String skuName,
        BigDecimal unitPrice, long stockQuantity, long reservedQuantity,
        boolean active, long rowVersion) {
    public long availableQuantity() {
        return stockQuantity - reservedQuantity;
    }
}
