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

    /** Creates a new opaque session token for an authenticated identity. */
    public String create(UserIdentity identity) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(Objects.requireNonNull(identity, "identity"), clock.instant()));
        return token;
    }

    /** Returns a valid identity and refreshes the idle-session expiry. */
    public UserIdentity requireSession(String token) {
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
        return session.identity;
    }

    /** Revokes one token, returning its identity when it represented a live session. */
    public Optional<UserIdentity> revoke(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Session session = sessions.remove(token);
        return session == null ? Optional.empty() : Optional.of(session.identity);
    }

    /** Revokes every session that belongs to the supplied user. */
    public void revokeAllForUser(String userId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().identity.userId().equals(userId));
    }

    private static final class Session {
        private final UserIdentity identity;
        private volatile Instant lastTouched;

        private Session(UserIdentity identity, Instant lastTouched) {
            this.identity = identity;
            this.lastTouched = lastTouched;
        }

        private boolean expired(Instant now, Duration timeout) {
            return !lastTouched.plus(timeout).isAfter(now);
        }

        private void touch(Instant now) { lastTouched = now; }
    }
}
