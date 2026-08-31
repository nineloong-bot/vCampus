package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Administrative shop list projection. */
public record ShopAdminSummary(String shopId, String ownerUserId,
        String shopName, String category, ShopStatus status,
        long productCount, long rowVersion) implements Serializable { }
