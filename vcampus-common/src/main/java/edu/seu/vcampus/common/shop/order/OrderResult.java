package edu.seu.vcampus.common.shop.order;

import java.io.Serializable;
import java.util.List;

/** Order snapshots with a buyer-visible validation notice. */
public record OrderResult(List<OrderView> orders, String notice) implements Serializable {
    /** Copies result rows. */
    public OrderResult { orders = List.copyOf(orders); }
}
