package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductSummary(String productId, String shopId, String shopName,
        String productName, String category, String coverImageUrl, BigDecimal minimumPrice,
        long salesCount, Instant createdAt) implements Serializable { }
