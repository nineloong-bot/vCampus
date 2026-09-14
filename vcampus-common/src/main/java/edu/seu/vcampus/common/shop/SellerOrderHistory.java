package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

/**
 * 商家店铺历史订单列表视图对象。
 */
public record SellerOrderHistory(List<SellerOrderView> orders) implements Serializable {
    public SellerOrderHistory { orders = List.copyOf(orders); }
}
