package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;

/** Filters and zero-based paging parameters for account searches. */
public record UserSearchQuery(
        String keyword,
        UserRole role,
        AccountStatus status,
        int page,
        int pageSize) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Validates paging inputs and removes surrounding keyword whitespace. */
    public UserSearchQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
        keyword = keyword == null ? null : keyword.strip();
    }
}
