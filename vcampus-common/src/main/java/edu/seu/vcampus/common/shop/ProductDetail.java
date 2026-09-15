package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Carries immutable product detail data.
 * @param productId the product identifier
 * @param productName the product name
 * @param category the category
 * @param description the description
 * @param coverImageUrl the cover image url
 * @param status the status
 * @param salesCount the sales count
 * @param shop the shop
 * @param skus the skus
 * @param createdAt the created at
 */
public record ProductDetail(String productId, String productName, String category,
        String description, String coverImageUrl, ProductStatus status, long salesCount, ShopSummary shop,
        List<ProductSkuView> skus, Instant createdAt) implements Serializable {
    /**
 * Validates and creates a product detail.
 * @param productId the product identifier
 * @param productName the product name
 * @param category the category
 * @param description the description
 * @param coverImageUrl the cover image url
 * @param status the status
 * @param salesCount the sales count
 * @param shop the shop
 * @param skus the skus
 * @param createdAt the created at
 */
public ProductDetail { skus = List.copyOf(skus); }

    /**
     * Validates and creates a product detail.
     * @param productId the product identifier
     * @param productName the product name
     * @param category the category
     * @param description the description
     * @param status the status
     * @param salesCount the sales count
     * @param shop the shop
     * @param skus the skus
     * @param createdAt the created at
     */
    public ProductDetail(String productId, String productName, String category, String description,
            ProductStatus status, long salesCount, ShopSummary shop, List<ProductSkuView> skus,
            Instant createdAt) {
        this(productId, productName, category, description, null, status, salesCount, shop, skus, createdAt);
    }
}
