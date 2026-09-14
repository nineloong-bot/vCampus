package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * 商家查询店铺订单列表的过滤参数对象。
 */
public record SellerOrderQuery(OrderStatus status, int pageNumber,
        int pageSize) implements Serializable { }
