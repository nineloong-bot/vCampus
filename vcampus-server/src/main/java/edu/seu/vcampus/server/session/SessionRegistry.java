package edu.seu.vcampus.server.session;

import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.security.UserIdentity;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Stores short-lived authenticated sessions in memory only. */
public final class SessionRegistry {
    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(30);
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Duration idleTimeout;

    /** Creates a registry with a 30-minute idle timeout and the system UTC clock. */
    public SessionRegistry() { this(Clock.systemUTC()); }

    /** Creates a registry with a 30-minute idle timeout and an injectable clock. */
    public SessionRegistry(Clock clock) { this(clock, DEFAULT_IDLE_TIMEOUT); }

    /** Creates a registry with explicit time dependencies for server configuration or tests. */
    public SessionRegistry(Clock clock, Duration idleTimeout) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout");
    }

    /** Creates a normal session without permissions or client metadata. */
    public String create(UserIdentity identity) {
        return create(identity, Set.of(), false, "unknown");
    }

    /** Creates a new opaque token whose authorization state is held only in memory. */
    public String create(UserIdentity identity, Set<String> permissions,
                         boolean restricted, String clientInstanceId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        SessionSnapshot snapshot = new SessionSnapshot(identity, permissions, restricted,
                clientInstanceId);
        sessions.put(token, new Session(snapshot, clock.instant()));
        return token;
    }

    /** Returns a valid identity and refreshes the idle-session expiry. */
    public UserIdentity requireSession(String token) {
        return requireSnapshot(token).identity();
    }

    /** Returns live session authorization state and refreshes its idle expiry. */
    public SessionSnapshot requireSnapshot(String token) {
        if (token == null) {
            throw new SessionExpiredException();
        }
        Session session = sessions.get(token);
        Instant now = clock.instant();
        if (session == null || session.expired(now, idleTimeout)) {
            sessions.remove(token, session);
            throw new SessionExpiredException();
        }
        session.touch(now);
        return session.snapshot;
    }

    /** Revokes one token, returning its identity when it represented a live session. */
    public Optional<UserIdentity> revoke(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Session session = sessions.remove(token);
        return session == null ? Optional.empty() : Optional.of(session.snapshot.identity());
    }

    /** Revokes every session that belongs to the supplied user. */
    public void revokeAllForUser(String userId) {
        sessions.entrySet().removeIf(entry ->
                entry.getValue().snapshot.identity().userId().equals(userId));
    }

    /** Immutable server-internal authorization state attached to one live session. */
    public record SessionSnapshot(UserIdentity identity, Set<String> permissions,
                                  boolean restricted, String clientInstanceId) {
        /** Validates identity and snapshots mutable authorization values. */
        public SessionSnapshot {
            Objects.requireNonNull(identity, "identity");
            permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
            clientInstanceId = clientInstanceId == null ? "unknown" : clientInstanceId;
        }
    }

    private static final class Session {
        private final SessionSnapshot snapshot;
        private volatile Instant lastTouched;

        private Session(SessionSnapshot snapshot, Instant lastTouched) {
            this.snapshot = snapshot;
            this.lastTouched = lastTouched;
        }

        private boolean expired(Instant now, Duration timeout) {
            return !lastTouched.plus(timeout).isAfter(now);
        }

        private void touch(Instant now) { lastTouched = now; }
    }
}
