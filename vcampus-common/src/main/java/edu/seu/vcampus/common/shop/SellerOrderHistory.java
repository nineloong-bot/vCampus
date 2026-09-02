package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

public record SellerOrderHistory(List<SellerOrderView> orders) implements Serializable {
    public SellerOrderHistory { orders = List.copyOf(orders); }
}
