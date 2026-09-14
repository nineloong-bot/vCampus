package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * 店铺商家修改商品上架或下架状态的请求命令。
 *  *
 *  * @param productId 商品标识
 *  * @param status 目标状态
 */
public record ChangeProductStatusCommand(String productId,
        ProductStatus targetStatus, long expectedVersion) implements Serializable { }
