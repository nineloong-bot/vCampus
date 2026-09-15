package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Administrative shop list projection. */
/**
 * Carries immutable shop admin summary data.
 * @param shopId the shop identifier
 * @param ownerUserId the owner user identifier
 * @param shopName the shop name
 * @param category the category
 * @param status the status
 * @param productCount the product count
 * @param rowVersion the row version
 */
public record ShopAdminSummary(String shopId, String ownerUserId,
        String shopName, String category, ShopStatus status,
        long productCount, long rowVersion) implements Serializable { }
