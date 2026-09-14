package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单条目明细视图。
 *  *
 *  * @param orderItemId 订单明细项标识
 *  * @param skuId 规格标识
 *  * @param productName 商品名称
 *  * @param skuName 规格名称
 *  * @param price 成交单价
 *  * @param quantity 购买数量
 */
public record OrderItemView(String orderItemId, String productName,
        String skuName, String shopName, BigDecimal unitPrice,
        int quantity, BigDecimal lineAmount) implements Serializable { }
