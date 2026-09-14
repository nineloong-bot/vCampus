package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * 店主更新店铺信息的请求命令。
 *  *
 *  * @param shopId 店铺标识
 *  * @param shopName 店铺名称
 *  * @param description 店铺介绍
 */
public record UpdateShopCommand(String shopName, String description,
        String category, String contact, long expectedVersion) implements Serializable { }
