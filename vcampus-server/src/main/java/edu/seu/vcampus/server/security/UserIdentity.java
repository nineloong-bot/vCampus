package edu.seu.vcampus.server.security;

import edu.seu.vcampus.common.user.UserRole;

import java.util.Objects;
import java.util.Set;

/** Immutable authenticated identity retained by an in-memory session. */
public record UserIdentity(String userId, String loginId, UserRole role,
                           Set<String> permissions, boolean restricted) {
    /** Validates identity fields and snapshots granted permissions. */
    public UserIdentity {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(loginId, "loginId");
        Objects.requireNonNull(role, "role");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }
}
