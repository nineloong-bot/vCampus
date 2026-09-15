package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Resumes a suspended shop. */
/**
 * Carries immutable resume shop command data.
 * @param shopId the shop identifier
 * @param expectedVersion the expected version
 */
public record ResumeShopCommand(String shopId,
        long expectedVersion) implements Serializable { }
