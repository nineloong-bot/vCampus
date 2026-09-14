package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商城主页推荐商品查询参数对象。
 *  *
 *  * @param category 分类筛选（可选）
 *  * @param limit 返回条数限制
 */
public record HomeProductQuery(BigDecimal minPrice, BigDecimal maxPrice,
        ProductSortMode sortMode, int pageNumber, int pageSize) implements Serializable { }
