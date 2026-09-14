package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 商城订单摘要视图对象。
 *  *
 *  * @param orderId 订单编号
 *  * @param shopName 店铺名称
 *  * @param status 订单状态
 *  * @param totalAmount 订单总额
 *  * @param createdAt 下单时间
 */
public record OrderSummary(String orderId, String orderGroupId, String orderNumber,
        String shopId, String shopName, BigDecimal orderAmount,
        OrderStatus status, Instant createdAt) implements Serializable { }
