package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Carries immutable home product query data.
 * @param minPrice the min price
 * @param maxPrice the max price
 * @param sortMode the sort mode
 * @param pageNumber the page number
 * @param pageSize the page size
 */
public record HomeProductQuery(BigDecimal minPrice, BigDecimal maxPrice,
        ProductSortMode sortMode, int pageNumber, int pageSize) implements Serializable { }
