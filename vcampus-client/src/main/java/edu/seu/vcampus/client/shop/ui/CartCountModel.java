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
    private long revision;

    public int totalQuantity() { return totalQuantity; }

    /** Starts a globally ordered cart operation. Only this revision may publish next. */
    public long beginUpdate() { return ++revision; }

    public void update(long updateRevision, CartView cart) {
        Objects.requireNonNull(cart, "cart");
        if (updateRevision != revision) return;
        setTotalQuantity(cart.items().stream().mapToInt(item -> item.quantity()).sum());
    }

    public void update(CartView cart) {
        long updateRevision = beginUpdate();
        update(updateRevision, cart);
    }

    public void clear(long updateRevision) {
        if (updateRevision == revision) setTotalQuantity(0);
    }

    public void clear() {
        long updateRevision = beginUpdate();
        clear(updateRevision);
    }

    /** Invalidates a read only when it is still the newest cart operation. */
    public void cancel(long updateRevision) {
        if (updateRevision != 0 && updateRevision == revision) revision++;
    }

    public void addListener(IntConsumer listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private void setTotalQuantity(int quantity) {
        if (quantity == totalQuantity) return;
        totalQuantity = quantity;
        List.copyOf(listeners).forEach(listener -> listener.accept(quantity));
    }
}
