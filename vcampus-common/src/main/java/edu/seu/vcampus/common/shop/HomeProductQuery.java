package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

public record HomeProductQuery(BigDecimal minPrice, BigDecimal maxPrice,
        ProductSortMode sortMode, int pageNumber, int pageSize) implements Serializable { }
