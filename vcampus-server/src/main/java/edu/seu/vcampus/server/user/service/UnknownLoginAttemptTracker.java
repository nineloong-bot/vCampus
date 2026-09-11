package edu.seu.vcampus.server.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks short-lived failures for normalized login identifiers that have no account.
 * It stores no credential or database data and removes expired entries during access.
 */
final class UnknownLoginAttemptTracker {
    private static final int MAX_FAILURES = 5;
    private final ConcurrentMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration lockoutDuration;

    UnknownLoginAttemptTracker(Clock clock, Duration lockoutDuration) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lockoutDuration = Objects.requireNonNull(lockoutDuration, "lockoutDuration");
    }

    String recordFailure(String normalizedLoginId) {
        Objects.requireNonNull(normalizedLoginId, "normalizedLoginId");
        Instant now = clock.instant();
        attempts.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        AtomicReference<String> result = new AtomicReference<>("AUTH_INVALID_CREDENTIALS");
        attempts.compute(normalizedLoginId, (ignored, current) -> {
            if (current != null && current.lockedUntil() != null
                    && current.lockedUntil().isAfter(now)) {
                result.set("AUTH_ACCOUNT_LOCKED");
                return current;
            }
            int failures = current == null ? 1 : current.failures() + 1;
            Instant expiresAt = now.plus(lockoutDuration);
            if (failures >= MAX_FAILURES) {
                result.set("AUTH_ACCOUNT_LOCKED");
                return new AttemptState(failures, expiresAt, expiresAt);
            }
            return new AttemptState(failures, expiresAt, null);
        });
        return result.get();
    }

    private record AttemptState(int failures, Instant expiresAt, Instant lockedUntil) { }
}
