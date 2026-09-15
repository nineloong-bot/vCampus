package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

/**
 * Carries immutable create product command data.
 * @param productName the product name
 * @param category the category
 * @param description the description
 * @param coverImageUrl the cover image url
 * @param skus the skus
 */
public record CreateProductCommand(String productName, String category,
        String description, String coverImageUrl, List<CreateSkuCommand> skus) implements Serializable {
    /**
 * Validates and creates a create product command.
 * @param productName the product name
 * @param category the category
 * @param description the description
 * @param coverImageUrl the cover image url
 * @param skus the skus
 */
public CreateProductCommand { skus = List.copyOf(skus); }

    /**
     * Validates and creates a create product command.
     * @param productName the product name
     * @param category the category
     * @param description the description
     * @param skus the skus
     */
    public CreateProductCommand(String productName, String category, String description,
            List<CreateSkuCommand> skus) {
        this(productName, category, description, null, skus);
    }
}
