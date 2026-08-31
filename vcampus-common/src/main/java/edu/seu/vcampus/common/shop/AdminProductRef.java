package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Administrator-scoped reference to one product inside an explicitly selected shop. */
public record AdminProductRef(String shopId, String productId) implements Serializable { }
