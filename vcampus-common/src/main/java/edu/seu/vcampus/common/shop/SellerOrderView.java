package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SellerOrderView(String orderId, String orderNumber, String buyerUserId,
        String shopId, String shopName, BigDecimal totalAmount, Instant paidAt,
        OrderStatus status, List<SellerOrderItemView> items) implements Serializable {
    public SellerOrderView { items = List.copyOf(items); }
}
