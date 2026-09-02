package edu.seu.vcampus.common.shop;

import java.io.Serializable;

public record SellerOrderQuery(OrderStatus status, int pageNumber,
        int pageSize) implements Serializable { }
