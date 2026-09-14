package edu.seu.vcampus.common.shop;

/**
 * 支付单结算状态枚举（待支付、已支付、已关闭）。
 */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    CANCELLED,
    EXPIRED
}
