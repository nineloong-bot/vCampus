package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.time.Instant;

/** Serializable seller/admin projection of a shop. */
/**
 * Carries immutable shop view data.
 * @param shopId the shop identifier
 * @param ownerUserId the owner user identifier
 * @param shopName the shop name
 * @param description the description
 * @param category the category
 * @param contact the contact
 * @param status the status
 * @param suspensionReason the suspension reason
 * @param suspendedByUserId the suspended by user identifier
 * @param suspendedAt the suspended at
 * @param rowVersion the row version
 */
public record ShopView(String shopId, String ownerUserId, String shopName,
        String description, String category, String contact, ShopStatus status,
        String suspensionReason, String suspendedByUserId, Instant suspendedAt,
        long rowVersion) implements Serializable { }
