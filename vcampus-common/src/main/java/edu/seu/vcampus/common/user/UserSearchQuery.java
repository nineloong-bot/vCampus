package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;

/** Bounded administrator account search query. */
public record UserSearchQuery(String keyword, UserRole role, AccountStatus status,
        int page, int pageSize) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
