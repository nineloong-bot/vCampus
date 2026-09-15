package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Submits an owned draft for administrative review. */
/**
 * Carries immutable submit seller application command data.
 * @param applicationId the application identifier
 * @param expectedVersion the expected version
 */
public record SubmitSellerApplicationCommand(String applicationId,
        long expectedVersion) implements Serializable { }
