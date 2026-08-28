package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;

/** Non-sensitive account summary for administrator screens. */
public record UserSummary(String userId, String loginId, UserRole role,
        AccountStatus status, long rowVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
