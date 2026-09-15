package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * Carries immutable update shop command data.
 * @param shopName the shop name
 * @param description the description
 * @param category the category
 * @param contact the contact
 * @param expectedVersion the expected version
 */
public record UpdateShopCommand(String shopName, String description,
        String category, String contact, long expectedVersion) implements Serializable { }
