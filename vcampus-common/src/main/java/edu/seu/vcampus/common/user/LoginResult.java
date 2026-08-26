package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/** Authenticated identity and in-memory session returned after login. */
public record LoginResult(
        String sessionToken,
        UserView user,
        Set<String> permissions,
        boolean mustChangePassword) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public LoginResult {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
