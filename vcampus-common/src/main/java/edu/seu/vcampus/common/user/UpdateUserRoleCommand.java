package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Requests a role change guarded by the account's current row version. */
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
