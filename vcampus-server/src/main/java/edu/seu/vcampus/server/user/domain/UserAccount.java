package edu.seu.vcampus.server.user.domain;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/** Complete server-side persistence model for one user account. */
public record UserAccount(
        String userId,
        String loginId,
        String passwordHash,
        String passwordSalt,
        int passwordIterations,
        UserRole role,
        AccountStatus accountStatus,
        boolean mustChangePassword,
        int failedLoginCount,
        LocalDateTime lockedUntil,
        LocalDateTime lastLoginAt,
        long rowVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Validates fields required by the account schema. */
    public UserAccount {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(loginId, "loginId");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(passwordSalt, "passwordSalt");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(accountStatus, "accountStatus");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (passwordIterations <= 0 || failedLoginCount < 0 || rowVersion < 0) {
            throw new IllegalArgumentException("numeric account fields are invalid");
        }
    }

    /** Returns a copy with a different account status. */
    public UserAccount withStatus(AccountStatus status) {
        return copy(role, status);
    }

    /** Returns a copy with a different base role. */
    public UserAccount withRole(UserRole newRole) {
        return copy(newRole, accountStatus);
    }

    private UserAccount copy(UserRole newRole, AccountStatus newStatus) {
        return new UserAccount(userId, loginId, passwordHash, passwordSalt,
                passwordIterations, newRole, newStatus, mustChangePassword,
                failedLoginCount, lockedUntil, lastLoginAt, rowVersion,
                createdAt, updatedAt);
    }
}
