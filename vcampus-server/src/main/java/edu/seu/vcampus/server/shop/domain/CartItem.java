package edu.seu.vcampus.server.shop.domain;

import java.time.Instant;

/** Provides cart item behavior. */
public record CartItem(String cartItemId, String cartId, String skuId,
        long quantity, long rowVersion, Instant createdAt, Instant updatedAt) { }
