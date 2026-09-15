package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * Carries immutable admin create product command data.
 * @param shopId the shop identifier
 * @param command the command
 */
public record AdminCreateProductCommand(String shopId,
        CreateProductCommand command) implements Serializable { }
