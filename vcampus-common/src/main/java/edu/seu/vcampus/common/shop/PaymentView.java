package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentView(String paymentId, String orderGroupId,
        String paymentNumber, BigDecimal amount, PaymentStatus status,
        PaymentChannel successfulChannel, Instant expiresAt,
        Instant completedAt, long rowVersion) implements Serializable { }
