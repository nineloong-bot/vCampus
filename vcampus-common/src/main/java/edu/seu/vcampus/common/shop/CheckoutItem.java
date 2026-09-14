package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 结算清单项参数对象。
 *  *
 *  * @param skuId 商品规格标识
 *  * @param quantity 购买数量
 */
public record CheckoutItem(String cartItemId, BigDecimal displayedUnitPrice)
        implements Serializable { }
