package edu.seu.vcampus.common.shop.order;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Current purchasable quantities and prices that require buyer confirmation. */
public record CheckoutQuote(List<OrderLine> lines, BigDecimal amount, String notice) implements Serializable {
    /** Copies the revised selection; unavailable rows are omitted. */
    public CheckoutQuote { lines = List.copyOf(lines); }
}
