package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * 商城管理员更新商品基本信息的请求命令。
 *  *
 *  * @param productId 商品标识
 *  * @param name 商品名称
 *  * @param description 商品描述
 *  * @param category 分类名称
 *  * @param imageUrl 图片地址
 */
public record AdminUpdateProductCommand(String shopId,
        UpdateProductCommand command) implements Serializable { }
