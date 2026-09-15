package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Carries immutable payment view data.
 * @param paymentId the payment identifier
 * @param orderGroupId the order group identifier
 * @param paymentNumber the payment number
 * @param amount the amount
 * @param status the status
 * @param successfulChannel the successful channel
 * @param expiresAt the expires at
 * @param completedAt the completed at
 * @param rowVersion the row version
 */
public record PaymentView(String paymentId, String orderGroupId,
        String paymentNumber, BigDecimal amount, PaymentStatus status,
        PaymentChannel successfulChannel, Instant expiresAt,
        Instant completedAt, long rowVersion) implements Serializable { }
