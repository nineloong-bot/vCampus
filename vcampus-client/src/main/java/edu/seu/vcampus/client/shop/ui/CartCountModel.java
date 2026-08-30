package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.common.shop.CartView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Publishes the latest authoritative total quantity in the buyer cart. */
public final class CartCountModel {
    private final List<IntConsumer> listeners = new ArrayList<>();
    private int totalQuantity;

    public int totalQuantity() { return totalQuantity; }

    public void update(CartView cart) {
        Objects.requireNonNull(cart, "cart");
        setTotalQuantity(cart.items().stream().mapToInt(item -> item.quantity()).sum());
    }

    public void clear() { setTotalQuantity(0); }

    public void addListener(IntConsumer listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private void setTotalQuantity(int quantity) {
        if (quantity == totalQuantity) return;
        totalQuantity = quantity;
        List.copyOf(listeners).forEach(listener -> listener.accept(quantity));
    }
}
