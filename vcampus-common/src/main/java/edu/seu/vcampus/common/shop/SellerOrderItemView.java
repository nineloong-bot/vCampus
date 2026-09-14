package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商家店铺订单商品明细条目对象。
 */
public record SellerOrderItemView(String productId, String productName, String skuId,
        String skuName, int quantity, BigDecimal unitPrice,
        BigDecimal lineAmount) implements Serializable { }
