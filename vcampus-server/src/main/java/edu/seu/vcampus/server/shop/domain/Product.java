package edu.seu.vcampus.server.shop.domain;

import edu.seu.vcampus.common.shop.ProductStatus;

import java.time.Instant;

/**
 * 服务端商城商品聚合根领域记录模型。
 *  *
 *  * @param productId 商品标识
 *  * @param shopId 所属店铺标识
 *  * @param name 商品名称
 *  * @param description 商品描述
 *  * @param category 分类
 *  * @param imageUrl 图片地址
 *  * @param status 商品状态
 */
public record Product(String productId, String shopId, String productName,
        String normalizedProductName, String category, String description, String coverImageUrl,
        ProductStatus status,
        long salesCount, long rowVersion, Instant createdAt, Instant updatedAt) { }
