package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Public account projection returned to clients and other approved consumers.
 * It deliberately excludes passwords, password hashes, salts, and lockout counters.
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
