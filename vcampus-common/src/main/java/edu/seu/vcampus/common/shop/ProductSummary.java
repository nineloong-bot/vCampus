package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Carries immutable product summary data.
 * @param productId the product identifier
 * @param shopId the shop identifier
 * @param shopName the shop name
 * @param productName the product name
 * @param category the category
 * @param coverImageUrl the cover image url
 * @param minimumPrice the minimum price
 * @param salesCount the sales count
 * @param createdAt the created at
 */
public record ProductSummary(String productId, String shopId, String shopName,
        String productName, String category, String coverImageUrl, BigDecimal minimumPrice,
        long salesCount, Instant createdAt) implements Serializable {
    /**
     * Validates and creates a product summary.
     * @param productId the product identifier
     * @param shopId the shop identifier
     * @param shopName the shop name
     * @param productName the product name
     * @param category the category
     * @param minimumPrice the minimum price
     * @param salesCount the sales count
     * @param createdAt the created at
     */
    public ProductSummary(String productId, String shopId, String shopName, String productName,
            String category, BigDecimal minimumPrice, long salesCount, Instant createdAt) {
        this(productId, shopId, shopName, productName, category, null, minimumPrice, salesCount, createdAt);
    }
}
