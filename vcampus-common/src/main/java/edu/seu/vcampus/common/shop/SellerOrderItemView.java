package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Carries immutable seller order item view data.
 * @param productId the product identifier
 * @param productName the product name
 * @param skuId the sku identifier
 * @param skuName the sku name
 * @param quantity the quantity
 * @param unitPrice the unit price
 * @param lineAmount the line amount
 */
public record SellerOrderItemView(String productId, String productName, String skuId,
        String skuName, int quantity, BigDecimal unitPrice,
        BigDecimal lineAmount) implements Serializable { }
