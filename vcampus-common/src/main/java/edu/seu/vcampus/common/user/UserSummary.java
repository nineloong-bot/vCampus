package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Safe compact account projection returned by paged administrator searches. */
public record UserSummary(String userId, String loginId, UserRole role,
                          AccountStatus accountStatus, LocalDateTime lastLoginAt,
                          long rowVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
