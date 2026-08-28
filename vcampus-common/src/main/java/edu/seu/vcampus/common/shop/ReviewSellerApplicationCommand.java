package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Approves or rejects a pending seller application. */
public record ReviewSellerApplicationCommand(String applicationId,
        SellerReviewDecision decision, String reason,
        long expectedVersion) implements Serializable { }
