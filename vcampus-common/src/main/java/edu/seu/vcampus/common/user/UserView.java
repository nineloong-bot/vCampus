package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Public account projection returned to clients and other approved consumers.
 * It deliberately excludes passwords, password hashes, salts, and lockout counters.
 */
/**
 * Carries immutable user view data.
 * @param userId the user identifier
 * @param loginId the login identifier
 * @param role the role
 * @param accountStatus the account status
 * @param mustChangePassword the must change password
 * @param lastLoginAt the last login at
 * @param rowVersion the row version
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
public record UserView(
        String userId,
        String loginId,
        UserRole role,
        AccountStatus accountStatus,
        boolean mustChangePassword,
        LocalDateTime lastLoginAt,
        long rowVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
