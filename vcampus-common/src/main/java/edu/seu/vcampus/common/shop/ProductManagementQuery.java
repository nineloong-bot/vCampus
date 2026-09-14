package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * 商家后台商品列表查询参数对象。
 */
public record ProductManagementQuery(String shopId, ProductStatus status, String keyword,
        int pageNumber, int pageSize) implements Serializable { }
