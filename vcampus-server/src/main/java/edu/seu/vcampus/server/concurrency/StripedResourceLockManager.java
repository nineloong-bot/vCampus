package edu.seu.vcampus.server.concurrency;

import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Resource lock manager backed by 256 fair lock stripes. */
public final class StripedResourceLockManager implements ResourceLockManager {
    private static final int STRIPE_COUNT = 256;
    private final ReentrantLock[] stripes = new ReentrantLock[STRIPE_COUNT];
    private final Consumer<ResourceKey> acquisitionObserver;

    /** Creates a lock manager without acquisition observation. */
    public StripedResourceLockManager() {
        this(ignored -> { });
    }

    StripedResourceLockManager(Consumer<ResourceKey> acquisitionObserver) {
        this.acquisitionObserver = Objects.requireNonNull(acquisitionObserver, "acquisitionObserver");
        for (int index = 0; index < stripes.length; index++) {
            stripes[index] = new ReentrantLock(true);
        }
    }

    /** Acquires each affected stripe once in canonical index order. */
    @Override
    public <T> T withLocks(List<ResourceKey> orderedKeys, Supplier<T> action) {
        List<ResourceKey> keys = List.copyOf(orderedKeys);
        Objects.requireNonNull(action, "action");
        Map<Integer, ResourceKey> targets = new TreeMap<>();
        for (ResourceKey key : keys) {
            targets.putIfAbsent(stripeIndex(key), key);
        }
        for (Map.Entry<Integer, ResourceKey> target : targets.entrySet()) {
            stripes[target.getKey()].lock();
            acquisitionObserver.accept(target.getValue());
        }
        try {
            return action.get();
        } finally {
            List<Integer> indexes = List.copyOf(targets.keySet());
            for (int index = indexes.size() - 1; index >= 0; index--) {
                stripes[indexes.get(index)].unlock();
            }
        }
    }

    static int stripeIndex(ResourceKey key) {
        return Math.floorMod(key.hashCode(), STRIPE_COUNT);
    }
}
