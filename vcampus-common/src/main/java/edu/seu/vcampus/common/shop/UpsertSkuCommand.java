package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 新增或更新商品规格的请求命令。
 *  *
 *  * @param productId 商品标识
 *  * @param skuName 规格名称
 *  * @param price 单价（分）
 *  * @param stock 库存量
 */
public record UpsertSkuCommand(String skuId, String skuName, BigDecimal unitPrice,
        long stockQuantity, boolean active, long expectedVersion) implements Serializable { }
