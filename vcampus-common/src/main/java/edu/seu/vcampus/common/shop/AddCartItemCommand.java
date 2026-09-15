package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * Carries immutable add cart item command data.
 * @param skuId the sku identifier
 * @param quantity the quantity
 */
public record AddCartItemCommand(String skuId, int quantity) implements Serializable { }
