package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Safe audit projection that excludes client addresses and authentication secrets. */
/**
 * Carries immutable security audit view data.
 * @param auditId the audit identifier
 * @param actorUserId the actor user identifier
 * @param actionCode the action code
 * @param targetType the target type
 * @param targetId the target identifier
 * @param resultCode the result code
 * @param createdAt the created at
 */
public record SecurityAuditView(
        String auditId,
        String actorUserId,
        String actionCode,
        String targetType,
        String targetId,
        String resultCode,
        LocalDateTime createdAt
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
