package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 店铺内商品列表查询参数对象。
 */
public record ShopProductQuery(String shopId, String keyword, String category,
        BigDecimal minPrice, BigDecimal maxPrice, ProductSortMode sortMode,
        int pageNumber, int pageSize) implements Serializable { }
