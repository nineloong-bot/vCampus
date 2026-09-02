package edu.seu.vcampus.server.concurrency;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StripedResourceLockManagerTest {
    @Test
    void serializesActionsForSameResource() throws Exception {
        var locks = new StripedResourceLockManager();
        var inside = new AtomicInteger();
        var maximum = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(20)) {
            var tasks = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> pool.submit(() -> locks.withLocks(
                            List.of(new ResourceKey("SKU", "sku-1")), () -> {
                                maximum.accumulateAndGet(inside.incrementAndGet(), Math::max);
                                Thread.yield();
                                inside.decrementAndGet();
                                return null;
                            }))).toList();
            for (var task : tasks) {
                task.get();
            }
        }
        assertThat(maximum).hasValue(1);
    }

    @Test
    void acquiresUniqueStripesInCanonicalOrderEvenWhenCallersDisagree() {
        var acquired = new ConcurrentLinkedQueue<ResourceKey>();
        var locks = new StripedResourceLockManager(acquired::add);
        var byStripe = new java.util.TreeMap<Integer, ResourceKey>();
        for (int index = 0; byStripe.size() < 3; index++) {
            ResourceKey key = new ResourceKey("RESOURCE", "key-" + index);
            byStripe.putIfAbsent(StripedResourceLockManager.stripeIndex(key), key);
        }
        var reversed = new java.util.ArrayList<>(byStripe.values());
        java.util.Collections.reverse(reversed);

        locks.withLocks(reversed, () -> null);

        assertThat(acquired).containsExactlyElementsOf(byStripe.values());
    }
}
