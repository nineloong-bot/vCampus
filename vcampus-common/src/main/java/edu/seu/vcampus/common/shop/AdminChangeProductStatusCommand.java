package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * Carries immutable admin change product status command data.
 * @param shopId the shop identifier
 * @param command the command
 */
public record AdminChangeProductStatusCommand(String shopId,
        ChangeProductStatusCommand command) implements Serializable { }
