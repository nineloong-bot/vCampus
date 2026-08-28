package edu.seu.vcampus.server.shop.domain;

import edu.seu.vcampus.common.shop.ProductStatus;

import java.time.Instant;

public record Product(String productId, String shopId, String productName,
        String category, String description, ProductStatus status,
        long salesCount, long rowVersion, Instant createdAt, Instant updatedAt) { }
