package edu.seu.vcampus.common.shop;

/**
 * 商城订单状态枚举（待支付、已支付、已完成、已取消等）。
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PREPARING,
    SHIPPED,
    COMPLETED,
    CANCELLED
}
