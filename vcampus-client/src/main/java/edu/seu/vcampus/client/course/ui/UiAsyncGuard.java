package edu.seu.vcampus.client.course.ui;

import java.util.concurrent.atomic.AtomicLong;

/** Rejects stale asynchronous UI results and all results after a view is closed. */
final class UiAsyncGuard {
    private final AtomicLong sequence = new AtomicLong();
    private volatile boolean active = true;

    long begin() {
        return sequence.incrementAndGet();
    }

    boolean accepts(long request) {
        return active && sequence.get() == request;
    }

    void activate() {
        active = true;
    }

    void deactivate() {
        active = false;
        sequence.incrementAndGet();
    }
}
