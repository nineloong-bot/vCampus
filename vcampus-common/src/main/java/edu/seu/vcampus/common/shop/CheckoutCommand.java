package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

public record CheckoutCommand(List<CheckoutItem> items, boolean acceptLatestPrice)
        implements Serializable {
    public CheckoutCommand {
        items = List.copyOf(items);
    }
}
