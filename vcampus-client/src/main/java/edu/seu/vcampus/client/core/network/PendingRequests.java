package edu.seu.vcampus.client.core.network;

import edu.seu.vcampus.common.protocol.Message;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Correlates response messages with pending client requests. */
public final class PendingRequests {
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, CompletableFuture<Message>> requests =
            new ConcurrentHashMap<>();

    /** Creates a registry using the supplied timeout scheduler. */
    public PendingRequests(ScheduledExecutorService scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Registers a unique request id and schedules its timeout. */
    public CompletableFuture<Message> register(String requestId, Duration timeout) {
        Objects.requireNonNull(requestId, "requestId");
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        var future = new CompletableFuture<Message>();
        if (requests.putIfAbsent(requestId, future) != null) {
            throw new IllegalArgumentException("Duplicate request id: " + requestId);
        }
        var timeoutTask = scheduler.schedule(
                () -> timeout(requestId, future), timeout.toMillis(), TimeUnit.MILLISECONDS);
        future.whenComplete((ignored, error) -> timeoutTask.cancel(false));
        return future;
    }

    /** Completes the pending request matching the response request id. */
    public boolean complete(Message response) {
        CompletableFuture<Message> future = requests.remove(response.requestId());
        return future != null && future.complete(response);
    }

    /** Fails and removes one pending request. */
    public boolean fail(String requestId, Throwable error) {
        CompletableFuture<Message> future = requests.remove(requestId);
        return future != null && future.completeExceptionally(error);
    }

    /** Fails all pending requests, typically after connection loss. */
    public void failAll(Throwable error) {
        requests.forEach((requestId, future) -> {
            if (requests.remove(requestId, future)) {
                future.completeExceptionally(error);
            }
        });
    }

    /** Returns the current number of pending requests. */
    public int size() {
        return requests.size();
    }

    private void timeout(String requestId, CompletableFuture<Message> future) {
        if (requests.remove(requestId, future)) {
            future.completeExceptionally(new TimeoutException("Request timed out: " + requestId));
        }
    }
}
