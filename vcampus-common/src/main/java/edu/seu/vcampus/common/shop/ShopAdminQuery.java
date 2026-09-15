package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Administrative shop search criteria. */
/**
 * Carries immutable shop admin query data.
 * @param keyword the keyword
 * @param status the status
 * @param pageNumber the page number
 * @param pageSize the page size
 */
public record ShopAdminQuery(String keyword, ShopStatus status,
        int pageNumber, int pageSize) implements Serializable { }
