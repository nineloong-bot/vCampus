package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 支付单详细视图对象。
 *  *
 *  * @param paymentId 支付单编号
 *  * @param orderGroupId 关联订单组编号
 *  * @param amount 支付金额（分）
 *  * @param status 支付状态
 */
public record PaymentView(String paymentId, String orderGroupId,
        String paymentNumber, BigDecimal amount, PaymentStatus status,
        PaymentChannel successfulChannel, Instant expiresAt,
        Instant completedAt, long rowVersion) implements Serializable { }
