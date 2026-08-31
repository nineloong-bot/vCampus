package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Administrative shop search criteria. */
public record ShopAdminQuery(String keyword, ShopStatus status,
        int pageNumber, int pageSize) implements Serializable { }
