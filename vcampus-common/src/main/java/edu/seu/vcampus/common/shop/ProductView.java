package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

/**
 * Carries immutable product view data.
 * @param productId the product identifier
 * @param productName the product name
 * @param category the category
 * @param description the description
 * @param coverImageUrl the cover image url
 * @param status the status
 * @param salesCount the sales count
 * @param rowVersion the row version
 * @param skus the skus
 */
public record ProductView(String productId, String productName, String category,
        String description, String coverImageUrl, ProductStatus status, long salesCount, long rowVersion,
        List<ProductSkuView> skus) implements Serializable {
    /**
 * Validates and creates a product view.
 * @param productId the product identifier
 * @param productName the product name
 * @param category the category
 * @param description the description
 * @param coverImageUrl the cover image url
 * @param status the status
 * @param salesCount the sales count
 * @param rowVersion the row version
 * @param skus the skus
 */
public ProductView { skus = List.copyOf(skus); }

    /**
     * Validates and creates a product view.
     * @param productId the product identifier
     * @param productName the product name
     * @param category the category
     * @param description the description
     * @param status the status
     * @param salesCount the sales count
     * @param rowVersion the row version
     * @param skus the skus
     */
    public ProductView(String productId, String productName, String category, String description,
            ProductStatus status, long salesCount, long rowVersion, List<ProductSkuView> skus) {
        this(productId, productName, category, description, null, status, salesCount, rowVersion, skus);
    }
}
