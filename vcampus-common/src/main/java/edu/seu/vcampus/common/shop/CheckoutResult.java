package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 订单结算结果对象。
 *  *
 *  * @param orderGroupId 订单组编号
 *  * @param paymentId 关联支付流水编号
 *  * @param totalAmount 订单应付总金额（分）
 *  * @param createdAt 创建时间
 */
public record CheckoutResult(String orderGroupId, String paymentId,
        String paymentNumber, BigDecimal totalAmount, Instant expiresAt,
        List<OrderSummary> orders) implements Serializable {
    public CheckoutResult {
        orders = List.copyOf(orders);
    }
}
