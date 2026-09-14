package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * 商品详细视图对象，包含商品信息、店铺信息及规格列表。
 */
public record ProductDetail(String productId, String productName, String category,
        String description, String coverImageUrl, ProductStatus status, long salesCount, ShopSummary shop,
        List<ProductSkuView> skus, Instant createdAt) implements Serializable {
    public ProductDetail { skus = List.copyOf(skus); }

    public ProductDetail(String productId, String productName, String category, String description,
            ProductStatus status, long salesCount, ShopSummary shop, List<ProductSkuView> skus,
            Instant createdAt) {
        this(productId, productName, category, description, null, status, salesCount, shop, skus, createdAt);
    }
}
