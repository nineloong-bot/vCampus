package edu.seu.vcampus.server.shop.domain;

import java.time.Instant;

/**
 * 服务端商城购物车明细领域记录模型。
 *  *
 *  * @param skuId 商品规格标识
 *  * @param quantity 选购数量
 */
public record CartItem(String cartItemId, String cartId, String skuId,
        long quantity, long rowVersion, Instant createdAt, Instant updatedAt) { }
