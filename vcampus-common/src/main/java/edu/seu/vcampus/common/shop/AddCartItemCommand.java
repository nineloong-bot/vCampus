package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * 向购物车添加商品规格项的请求命令。
 *  *
 *  * @param skuId 商品规格标识
 *  * @param quantity 选购数量
 */
public record AddCartItemCommand(String skuId, int quantity) implements Serializable { }
