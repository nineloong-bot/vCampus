package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Carries immutable order item view data.
 * @param orderItemId the order item identifier
 * @param productName the product name
 * @param skuName the sku name
 * @param shopName the shop name
 * @param unitPrice the unit price
 * @param quantity the quantity
 * @param lineAmount the line amount
 */
public record OrderItemView(String orderItemId, String productName,
        String skuName, String shopName, BigDecimal unitPrice,
        int quantity, BigDecimal lineAmount) implements Serializable { }
