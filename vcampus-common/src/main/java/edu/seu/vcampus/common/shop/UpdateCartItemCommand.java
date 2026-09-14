package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * 修改购物车中商品规格数量的请求命令。
 *  *
 *  * @param skuId 商品规格标识
 *  * @param quantity 更新后的数量
 */
public record UpdateCartItemCommand(String cartItemId, int quantity,
        long expectedVersion) implements Serializable { }
