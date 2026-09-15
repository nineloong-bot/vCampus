package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/** Public result of a successful login without password material. */
/**
 * Carries immutable login result data.
 * @param sessionToken the session token
 * @param user the user
 * @param permissions the permissions
 * @param mustChangePassword the must change password
 */
public record LoginResult(
        String sessionToken,
        UserView user,
        Set<String> permissions,
        boolean mustChangePassword) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Validates required values and snapshots the permission set. */
    public LoginResult {
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(user, "user");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }
}
