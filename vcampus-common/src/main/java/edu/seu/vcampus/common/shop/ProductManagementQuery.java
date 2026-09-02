package edu.seu.vcampus.common.shop;

import java.io.Serializable;

public record ProductManagementQuery(String shopId, ProductStatus status, String keyword,
        int pageNumber, int pageSize) implements Serializable { }
