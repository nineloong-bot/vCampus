package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Filters the administrator-only security audit query with zero-based paging. */
public record SecurityAuditQuery(
        String userId,
        String actionCode,
        String resultCode,
        LocalDateTime fromInclusive,
        LocalDateTime toExclusive,
        int page,
        int pageSize
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
