package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Safe compact account projection returned by paged administrator searches. */
/**
 * Carries immutable user summary data.
 * @param userId the user identifier
 * @param loginId the login identifier
 * @param role the role
 * @param accountStatus the account status
 * @param lastLoginAt the last login at
 * @param rowVersion the row version
 */
public record UserSummary(String userId, String loginId, UserRole role,
                          AccountStatus accountStatus, LocalDateTime lastLoginAt,
                          long rowVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
