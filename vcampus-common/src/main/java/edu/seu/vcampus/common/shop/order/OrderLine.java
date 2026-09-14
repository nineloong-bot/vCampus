package edu.seu.vcampus.common.shop.order;

import java.io.Serializable;
import java.math.BigDecimal;

/** Selected SKU and buyer-confirmed price used for authoritative checkout. */
public record OrderLine(String skuId, int quantity, BigDecimal expectedUnitPrice) implements Serializable {
    /** Rejects invalid selections before a transaction is opened. */
    public OrderLine {
        if (skuId == null || skuId.isBlank() || skuId.length() > 36 || quantity < 1
                || expectedUnitPrice == null || expectedUnitPrice.signum() <= 0
                || expectedUnitPrice.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("ORDER_INVALID_REQUEST");
    }
}
