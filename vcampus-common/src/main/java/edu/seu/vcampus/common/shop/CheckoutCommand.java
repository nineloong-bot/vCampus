package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

/**
 * Carries immutable checkout command data.
 * @param items the items
 * @param acceptLatestPrice the accept latest price
 */
public record CheckoutCommand(List<CheckoutItem> items, boolean acceptLatestPrice)
        implements Serializable {
    /**
     * Validates and creates a checkout command.
     * @param items the items
     * @param acceptLatestPrice the accept latest price
     */
    public CheckoutCommand {
        items = List.copyOf(items);
    }
}
