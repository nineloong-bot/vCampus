package edu.seu.vcampus.common.shop.order;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Safe buyer/seller projection containing a public nickname, never buyer identity. */
public record OrderView(String orderId, String orderGroupId, String shopId, String shopName,
                        String buyerNickname, String state, BigDecimal amount, Instant createdAt,
                        Instant expiresAt, String refundReason, List<OrderItem> items) implements Serializable {
    /** Defensively copies persisted line snapshots. */
    public OrderView { items = List.copyOf(items); }
}
