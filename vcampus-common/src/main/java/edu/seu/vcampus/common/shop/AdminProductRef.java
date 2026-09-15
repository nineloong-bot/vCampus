package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Administrator-scoped reference to one product inside an explicitly selected shop. */
/**
 * Carries immutable admin product ref data.
 * @param shopId the shop identifier
 * @param productId the product identifier
 */
public record AdminProductRef(String shopId, String productId) implements Serializable { }
