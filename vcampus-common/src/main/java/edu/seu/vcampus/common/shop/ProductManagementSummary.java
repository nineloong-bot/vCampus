package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商家后台商品管理摘要视图对象。
 */
public record ProductManagementSummary(String productId, String productName, ProductStatus status,
        long skuCount, BigDecimal minimumPrice, long totalStock, long reservedStock,
        long salesCount, long rowVersion) implements Serializable { }
