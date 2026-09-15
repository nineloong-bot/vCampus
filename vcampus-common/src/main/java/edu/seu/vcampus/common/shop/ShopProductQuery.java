package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Carries immutable shop product query data.
 * @param shopId the shop identifier
 * @param keyword the keyword
 * @param category the category
 * @param minPrice the min price
 * @param maxPrice the max price
 * @param sortMode the sort mode
 * @param pageNumber the page number
 * @param pageSize the page size
 */
public record ShopProductQuery(String shopId, String keyword, String category,
        BigDecimal minPrice, BigDecimal maxPrice, ProductSortMode sortMode,
        int pageNumber, int pageSize) implements Serializable { }
