package edu.seu.vcampus.common.shop;

import java.io.Serializable;

public record SimulatePaymentCommand(String paymentId, PaymentChannel channel,
        PaymentAttemptStatus simulatedResult) implements Serializable { }
