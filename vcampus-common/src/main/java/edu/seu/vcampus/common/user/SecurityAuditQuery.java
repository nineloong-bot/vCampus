package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Filters the administrator-only security audit query with zero-based paging. */
/**
 * Carries immutable security audit query data.
 * @param userId the user identifier
 * @param actionCode the action code
 * @param resultCode the result code
 * @param fromInclusive the from inclusive
 * @param toExclusive the to exclusive
 * @param page the page
 * @param pageSize the page size
 */
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
