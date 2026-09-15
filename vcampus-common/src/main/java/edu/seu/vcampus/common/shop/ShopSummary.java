package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * Carries immutable shop summary data.
 * @param shopId the shop identifier
 * @param shopName the shop name
 */
public record ShopSummary(String shopId, String shopName) implements Serializable { }
