package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

public record PaidOrderItemView(String productId, String productName,
        String skuId, String skuName, int quantity, BigDecimal unitPrice,
        BigDecimal lineAmount) implements Serializable {
    public PaidOrderItemView {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(skuName, "skuName");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(lineAmount, "lineAmount");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must be non-negative");
        }
        if (lineAmount.signum() < 0) {
            throw new IllegalArgumentException("lineAmount must be non-negative");
        }
        BigDecimal expectedLineAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        if (lineAmount.compareTo(expectedLineAmount) != 0) {
            throw new IllegalArgumentException("lineAmount must equal unitPrice * quantity");
        }
    }
}
