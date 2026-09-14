package edu.seu.vcampus.server.shop.domain;

import java.math.BigDecimal;

/**
 * 服务端商城商品规格领域记录模型。
 *  *
 *  * @param skuId 规格标识
 *  * @param productId 关联商品标识
 *  * @param skuName 规格名称
 *  * @param price 单价（分）
 *  * @param stock 库存数量
 */
public record ProductSku(String skuId, String productId, String skuName,
        BigDecimal unitPrice, long stockQuantity, long reservedQuantity,
        boolean active, long rowVersion) {
    public long availableQuantity() {
        return stockQuantity - reservedQuantity;
    }
}
