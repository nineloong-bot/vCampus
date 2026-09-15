package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Carries immutable cart item view data.
 * @param cartItemId the cart item identifier
 * @param productId the product identifier
 * @param productName the product name
 * @param skuId the sku identifier
 * @param skuName the sku name
 * @param shopId the shop identifier
 * @param shopName the shop name
 * @param displayedUnitPrice the displayed unit price
 * @param quantity the quantity
 * @param rowVersion the row version
 */
public record CartItemView(String cartItemId, String productId, String productName,
        String skuId, String skuName, String shopId, String shopName,
        BigDecimal displayedUnitPrice, int quantity,
        long rowVersion) implements Serializable { }
