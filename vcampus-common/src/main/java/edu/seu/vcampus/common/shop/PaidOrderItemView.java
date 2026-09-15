package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Carries immutable paid order item view data.
 * @param productId the product identifier
 * @param productName the product name
 * @param skuId the sku identifier
 * @param skuName the sku name
 * @param quantity the quantity
 * @param unitPrice the unit price
 * @param lineAmount the line amount
 */
public record PaidOrderItemView(String productId, String productName,
        String skuId, String skuName, int quantity, BigDecimal unitPrice,
        BigDecimal lineAmount) implements Serializable {
    /**
     * Validates and creates a paid order item view.
     * @param productId the product id
     * @param productName the product name
     * @param skuId the sku id
     * @param skuName the sku name
     * @param quantity the quantity
     * @param unitPrice the unit price
     * @param lineAmount the line amount
     */
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
