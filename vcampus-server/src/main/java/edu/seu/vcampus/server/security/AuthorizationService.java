package edu.seu.vcampus.server.security;

import edu.seu.vcampus.server.session.SessionRegistry;

import java.util.Objects;

/** Authorization adapter backed by the application's live session registry. */
public final class AuthorizationService implements AuthorizationPort {
    private final SessionRegistry sessions;

    /** Creates an authorization service for the supplied session registry. */
    public AuthorizationService(SessionRegistry sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /** Returns a session identity after checking token existence and expiry. */
    @Override public UserIdentity requireSession(String sessionToken) {
        return sessions.requireSession(sessionToken);
    }

    /** Enforces the first-password restriction before the normal permission check. */
    @Override public UserIdentity requirePermission(String sessionToken, String permissionCode) {
        UserIdentity identity = requireSession(sessionToken);
        if (identity.restricted()) {
            throw new InitialPasswordChangeRequiredException();
        }
        if (!identity.permissions().contains(permissionCode)) {
            throw new ForbiddenException();
        }
        return identity;
    }
}
