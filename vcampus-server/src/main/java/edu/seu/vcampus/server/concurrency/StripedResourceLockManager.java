package edu.seu.vcampus.server.concurrency;

import java.util.List;
import java.util.Objects;
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

    /** Acquires an immutable copy of the keys in the caller's declared order. */
    @Override
    public <T> T withLocks(List<ResourceKey> orderedKeys, Supplier<T> action) {
        List<ResourceKey> keys = List.copyOf(orderedKeys);
        Objects.requireNonNull(action, "action");
        for (ResourceKey key : keys) {
            stripe(key).lock();
            acquisitionObserver.accept(key);
        }
        try {
            return action.get();
        } finally {
            for (int index = keys.size() - 1; index >= 0; index--) {
                stripe(keys.get(index)).unlock();
            }
        }
    }

    private ReentrantLock stripe(ResourceKey key) {
        return stripes[Math.floorMod(key.hashCode(), stripes.length)];
    }
}
