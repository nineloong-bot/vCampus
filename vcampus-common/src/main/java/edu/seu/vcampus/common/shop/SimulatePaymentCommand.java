package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * Carries immutable simulate payment command data.
 * @param paymentId the payment identifier
 * @param channel the channel
 * @param simulatedResult the simulated result
 */
public record SimulatePaymentCommand(String paymentId, PaymentChannel channel,
        PaymentAttemptStatus simulatedResult) implements Serializable { }
