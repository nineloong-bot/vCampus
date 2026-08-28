package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.math.BigDecimal;

public record CheckoutItem(String cartItemId, BigDecimal displayedUnitPrice)
        implements Serializable { }
