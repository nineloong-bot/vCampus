package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Carries immutable checkout item data.
 * @param cartItemId the cart item identifier
 * @param displayedUnitPrice the displayed unit price
 */
public record CheckoutItem(String cartItemId, BigDecimal displayedUnitPrice)
        implements Serializable { }
