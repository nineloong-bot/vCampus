package edu.seu.vcampus.common.shop;

/**
 * 单次支付尝试的状态枚举（进行中、成功、失败）。
 */
public enum PaymentAttemptStatus {
    STARTED,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
