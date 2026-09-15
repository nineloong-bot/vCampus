package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * Carries immutable product management query data.
 * @param shopId the shop identifier
 * @param status the status
 * @param keyword the keyword
 * @param pageNumber the page number
 * @param pageSize the page size
 */
public record ProductManagementQuery(String shopId, ProductStatus status, String keyword,
        int pageNumber, int pageSize) implements Serializable { }
