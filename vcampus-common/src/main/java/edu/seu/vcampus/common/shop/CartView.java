package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车完整视图对象。
 *  *
 *  * @param userId 用户标识
 *  * @param items 购物车条目列表
 *  * @param totalAmount 购物车总金额（分）
 *  * @param totalCount 商品总件数
 */
public record CartView(String cartId, List<CartItemView> items,
        BigDecimal displayedTotal) implements Serializable {
    public CartView { items = List.copyOf(items); }
}
