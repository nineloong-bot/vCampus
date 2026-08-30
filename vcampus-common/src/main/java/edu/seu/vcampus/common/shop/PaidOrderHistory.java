package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

public record PaidOrderHistory(List<PaidOrderView> orders) implements Serializable {
    public PaidOrderHistory {
        orders = List.copyOf(orders);
    }
}
