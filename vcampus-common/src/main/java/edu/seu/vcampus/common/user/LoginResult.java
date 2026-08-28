package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/** Public result of a successful login without password material. */
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
