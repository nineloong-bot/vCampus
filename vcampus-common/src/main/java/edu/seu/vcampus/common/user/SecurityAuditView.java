package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Safe audit projection that excludes client addresses and authentication secrets. */
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
