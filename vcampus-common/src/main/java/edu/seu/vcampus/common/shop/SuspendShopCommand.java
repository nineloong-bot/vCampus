package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Suspends an active shop with an auditable reason. */
/**
 * Carries immutable suspend shop command data.
 * @param shopId the shop identifier
 * @param reason the reason
 * @param expectedVersion the expected version
 */
public record SuspendShopCommand(String shopId, String reason,
        long expectedVersion) implements Serializable { }
