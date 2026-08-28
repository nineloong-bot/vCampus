package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.time.Instant;

/** Serializable seller/admin projection of a shop. */
public record ShopView(String shopId, String ownerUserId, String shopName,
        String description, String category, String contact, ShopStatus status,
        String suspensionReason, String suspendedByUserId, Instant suspendedAt,
        long rowVersion) implements Serializable { }
