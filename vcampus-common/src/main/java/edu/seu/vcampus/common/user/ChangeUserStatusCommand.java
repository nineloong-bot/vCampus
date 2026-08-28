package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;

/** Administrator request to change an account status. */
public record ChangeUserStatusCommand(String userId, AccountStatus newStatus,
        String reason, long expectedVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
