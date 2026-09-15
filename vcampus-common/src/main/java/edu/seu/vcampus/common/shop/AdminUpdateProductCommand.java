package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * Carries immutable admin update product command data.
 * @param shopId the shop identifier
 * @param command the command
 */
public record AdminUpdateProductCommand(String shopId,
        UpdateProductCommand command) implements Serializable { }
