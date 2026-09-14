package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车商品项视图对象。
 *  *
 *  * @param skuId 商品规格标识
 *  * @param productId 所属商品标识
 *  * @param shopId 所属店铺标识
 *  * @param productName 商品名称
 *  * @param skuName 规格名称
 *  * @param price 单价（分）
 *  * @param quantity 数量
 *  * @param imageUrl 商品图片地址
 */
public record CartItemView(String cartItemId, String productId, String productName,
        String skuId, String skuName, String shopId, String shopName,
        BigDecimal displayedUnitPrice, int quantity,
        long rowVersion) implements Serializable { }
