package edu.seu.vcampus.server.security;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;

import java.util.Objects;

/**
 * Used only by authorization boundaries and other server-side business modules
 * to identify a user and the user's account state.
 * It contains no credentials, password hash, salt, permission collection,
 * session token, clientInstanceId, or initial-password-change state.
 */
public record UserIdentity(String userId, String loginId, UserRole role,
                           AccountStatus accountStatus) {
    /** Validates the non-sensitive identity fields. */
    public UserIdentity {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(loginId, "loginId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(accountStatus, "accountStatus");
    }
}
