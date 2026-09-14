package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 创建商品规格（SKU）的请求命令。
 *  *
 *  * @param productId 商品标识
 *  * @param skuName 规格名称
 *  * @param price 单价（分）
 *  * @param stock 初始库存量
 */
public record CreateSkuCommand(String skuName, BigDecimal unitPrice,
        long stockQuantity, boolean active) implements Serializable { }
