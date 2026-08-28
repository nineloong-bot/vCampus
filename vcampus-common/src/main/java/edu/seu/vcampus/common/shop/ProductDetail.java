package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record ProductDetail(String productId, String productName, String category,
        String description, ProductStatus status, long salesCount, ShopSummary shop,
        List<ProductSkuView> skus, Instant createdAt) implements Serializable {
    public ProductDetail { skus = List.copyOf(skus); }
}
