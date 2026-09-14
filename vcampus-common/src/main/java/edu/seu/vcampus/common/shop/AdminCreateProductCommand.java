package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * 商城管理员创建商品的请求命令。
 *  *
 *  * @param shopId 所属店铺标识
 *  * @param name 商品名称
 *  * @param description 商品描述
 *  * @param category 分类名称
 *  * @param imageUrl 图片地址
 */
public record AdminCreateProductCommand(String shopId,
        CreateProductCommand command) implements Serializable { }
