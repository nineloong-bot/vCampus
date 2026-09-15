package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * Carries immutable shop detail data.
 * @param shopId the shop identifier
 * @param shopName the shop name
 * @param description the description
 * @param category the category
 * @param contact the contact
 * @param shopStatus the shop status
 */
public record ShopDetail(String shopId, String shopName, String description,
        String category, String contact, ShopStatus shopStatus) implements Serializable { }
