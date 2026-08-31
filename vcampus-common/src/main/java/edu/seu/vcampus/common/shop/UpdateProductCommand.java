package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

public record UpdateProductCommand(String productId, String productName,
        String category, String description, String coverImageUrl, List<UpsertSkuCommand> skus,
        long expectedVersion) implements Serializable {
    public UpdateProductCommand { skus = List.copyOf(skus); }

    public UpdateProductCommand(String productId, String productName, String category,
            String description, List<UpsertSkuCommand> skus, long expectedVersion) {
        this(productId, productName, category, description, null, skus, expectedVersion);
    }
}
