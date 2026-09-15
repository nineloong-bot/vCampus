package edu.seu.vcampus.common.shop.order;

import java.io.Serializable;
import java.util.List;

/** Selected complete shop orders and optional refund explanation. */
public record OrderAction(List<String> orderIds, String reason) implements Serializable {
    /** Copies identifiers and bounds reasons to the persisted protocol limit. */
    public OrderAction {
        if (orderIds == null || orderIds.isEmpty() || orderIds.size() > 100)
            throw new IllegalArgumentException("ORDER_INVALID_REQUEST");
        orderIds = List.copyOf(orderIds);
        if (orderIds.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 36)
                || orderIds.stream().distinct().count() != orderIds.size())
            throw new IllegalArgumentException("ORDER_INVALID_REQUEST");
        reason = reason == null ? "" : reason.strip();
        if (reason.length() > 500) throw new IllegalArgumentException("ORDER_INVALID_REQUEST");
    }
}
