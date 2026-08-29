package edu.seu.vcampus.client.shop.ui.async;

import java.util.concurrent.atomic.AtomicLong;

/** Rejects completion callbacks from an earlier page request. */
public final class LatestRequest {
    private final AtomicLong sequence = new AtomicLong();
    private volatile boolean disposed;

    public long begin() {
        return sequence.incrementAndGet();
    }

    public boolean accepts(long id) {
        return !disposed && sequence.get() == id;
    }

    public void dispose() {
        disposed = true;
        sequence.incrementAndGet();
    }
}
