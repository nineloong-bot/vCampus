package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Requests an account lifecycle transition guarded by the current row version. */
public record ChangeUserStatusCommand(String userId, AccountStatus newStatus,
                                      String reason, long expectedVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Validates required target, status, reason, and optimistic-lock version fields. */
    public ChangeUserStatusCommand {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(newStatus, "newStatus");
        reason = Objects.requireNonNull(reason, "reason").strip();
        if (reason.isEmpty() || expectedVersion < 0) throw new IllegalArgumentException("invalid status change");
    }
}
