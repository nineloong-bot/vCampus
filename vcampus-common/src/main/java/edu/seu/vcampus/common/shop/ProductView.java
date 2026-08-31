package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

public record ProductView(String productId, String productName, String category,
        String description, String coverImageUrl, ProductStatus status, long salesCount, long rowVersion,
        List<ProductSkuView> skus) implements Serializable {
    public ProductView { skus = List.copyOf(skus); }

    public ProductView(String productId, String productName, String category, String description,
            ProductStatus status, long salesCount, long rowVersion, List<ProductSkuView> skus) {
        this(productId, productName, category, description, null, status, salesCount, rowVersion, skus);
    }
}
