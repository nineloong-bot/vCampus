package edu.seu.vcampus.server.session;

import edu.seu.vcampus.common.user.UserView;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory session store for the local account MVP. */
public final class SessionRegistry {
    private static final Duration TIMEOUT = Duration.ofMinutes(30);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public String create(UserView user, boolean restricted) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(user, restricted, Instant.now()));
        return token;
    }

    public Optional<Session> find(String token) {
        if (token == null) return Optional.empty();
        Session session = sessions.get(token);
        if (session == null || session.createdAt().plus(TIMEOUT).isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void revoke(String token) {
        if (token != null) sessions.remove(token);
    }

    public record Session(UserView user, boolean restricted, Instant createdAt) {
    }
}
