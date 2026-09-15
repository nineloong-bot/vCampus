package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Approves or rejects a pending seller application. */
/**
 * Carries immutable review seller application command data.
 * @param applicationId the application identifier
 * @param decision the decision
 * @param reason the reason
 * @param expectedVersion the expected version
 */
public record ReviewSellerApplicationCommand(String applicationId,
        SellerReviewDecision decision, String reason,
        long expectedVersion) implements Serializable { }
