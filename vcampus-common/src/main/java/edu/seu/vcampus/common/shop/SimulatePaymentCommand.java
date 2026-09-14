package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * 模拟支付扣款执行命令。
 *  *
 *  * @param paymentId 支付单编号
 *  * @param cardPassword 一卡通支付密码
 */
public record SimulatePaymentCommand(String paymentId, PaymentChannel channel,
        PaymentAttemptStatus simulatedResult) implements Serializable { }
