package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * 店铺简要摘要视图对象。
 */
public record ShopSummary(String shopId, String shopName) implements Serializable { }
