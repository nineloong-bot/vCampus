package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Retired compatibility contract for the permanently disabled role-change command.
 * Servers retain the type and command name for binary compatibility but reject every
 * request without changing account data.
 */
/**
 * Carries immutable update user role command data.
 * @param userId the user identifier
 * @param newRole the new role
 * @param expectedVersion the expected version
 */
public record UpdateUserRoleCommand(String userId, UserRole newRole,
                                    long expectedVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Validates the target account, requested role, and optimistic-lock version. */
    public UpdateUserRoleCommand {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(newRole, "newRole");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
    }
}
