package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

/**
 * Carries immutable update product command data.
 * @param productId the product identifier
 * @param productName the product name
 * @param category the category
 * @param description the description
 * @param coverImageUrl the cover image url
 * @param skus the skus
 * @param expectedVersion the expected version
 */
public record UpdateProductCommand(String productId, String productName,
        String category, String description, String coverImageUrl, List<UpsertSkuCommand> skus,
        long expectedVersion) implements Serializable {
    /**
 * Validates and creates a update product command.
 * @param productId the product identifier
 * @param productName the product name
 * @param category the category
 * @param description the description
 * @param coverImageUrl the cover image url
 * @param skus the skus
 * @param expectedVersion the expected version
 */
public UpdateProductCommand { skus = List.copyOf(skus); }

    /**
     * Validates and creates a update product command.
     * @param productId the product identifier
     * @param productName the product name
     * @param category the category
     * @param description the description
     * @param skus the skus
     * @param expectedVersion the expected version
     */
    public UpdateProductCommand(String productId, String productName, String category,
            String description, List<UpsertSkuCommand> skus, long expectedVersion) {
        this(productId, productName, category, description, null, skus, expectedVersion);
    }
}
