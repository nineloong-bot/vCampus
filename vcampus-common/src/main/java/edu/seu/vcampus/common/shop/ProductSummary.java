package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 商品简要展示信息视图对象。
 */
public record ProductSummary(String productId, String shopId, String shopName,
        String productName, String category, String coverImageUrl, BigDecimal minimumPrice,
        long salesCount, Instant createdAt) implements Serializable {
    public ProductSummary(String productId, String shopId, String shopName, String productName,
            String category, BigDecimal minimumPrice, long salesCount, Instant createdAt) {
        this(productId, shopId, shopName, productName, category, null, minimumPrice, salesCount, createdAt);
    }
}
