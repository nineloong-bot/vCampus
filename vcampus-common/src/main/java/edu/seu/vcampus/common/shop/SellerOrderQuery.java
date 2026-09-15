package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * Carries immutable seller order query data.
 * @param status the status
 * @param pageNumber the page number
 * @param pageSize the page size
 */
public record SellerOrderQuery(OrderStatus status, int pageNumber,
        int pageSize) implements Serializable { }
