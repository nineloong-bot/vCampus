package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * Carries immutable update cart item command data.
 * @param cartItemId the cart item identifier
 * @param quantity the quantity
 * @param expectedVersion the expected version
 */
public record UpdateCartItemCommand(String cartItemId, int quantity,
        long expectedVersion) implements Serializable { }
