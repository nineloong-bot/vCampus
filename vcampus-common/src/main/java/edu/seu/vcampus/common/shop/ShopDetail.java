package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * 店铺详细信息视图对象，包含店铺公告与店主联系方式。
 */
public record ShopDetail(String shopId, String shopName, String description,
        String category, String contact, ShopStatus shopStatus) implements Serializable { }
