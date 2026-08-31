package edu.seu.vcampus.server.shop.domain;

import edu.seu.vcampus.common.shop.ProductStatus;

import java.time.Instant;

public record Product(String productId, String shopId, String productName,
        String normalizedProductName, String category, String description, String coverImageUrl,
        ProductStatus status,
        long salesCount, long rowVersion, Instant createdAt, Instant updatedAt) { }
