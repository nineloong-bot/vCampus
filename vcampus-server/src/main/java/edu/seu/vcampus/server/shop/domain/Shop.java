package edu.seu.vcampus.server.shop.domain;

import edu.seu.vcampus.common.shop.ShopStatus;

import java.time.Instant;

/** Persistence model for an approved shop. */
public record Shop(String shopId, String ownerUserId, String shopName,
        String description, String category, String contact, ShopStatus status,
        String suspensionReason, String suspendedByUserId, Instant suspendedAt,
        long rowVersion, Instant createdAt, Instant updatedAt) { }
