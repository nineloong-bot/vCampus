package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * Carries immutable change product status command data.
 * @param productId the product identifier
 * @param targetStatus the target status
 * @param expectedVersion the expected version
 */
public record ChangeProductStatusCommand(String productId,
        ProductStatus targetStatus, long expectedVersion) implements Serializable { }
