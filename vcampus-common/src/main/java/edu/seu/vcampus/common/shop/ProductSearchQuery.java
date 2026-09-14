package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商城商品全局搜索参数对象。
 *  *
 *  * @param keyword 检索关键词
 *  * @param pageNo 页码
 *  * @param pageSize 每页条数
 */
public record ProductSearchQuery(String keyword, String category,
        BigDecimal minPrice, BigDecimal maxPrice, ProductSortMode sortMode,
        int pageNumber, int pageSize) implements Serializable { }
