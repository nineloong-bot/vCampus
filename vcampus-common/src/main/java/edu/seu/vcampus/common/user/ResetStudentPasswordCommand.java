package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Requests an administrator-controlled student password initialization.
 * This contract carries only the target student and optimistic-lock version;
 * it never carries a password, hash, salt, token, or client address.
 */
public record ResetStudentPasswordCommand(
        String targetUserId,
        long expectedRowVersion
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Validates the target identity and optimistic-lock version. */
    public ResetStudentPasswordCommand {
        Objects.requireNonNull(targetUserId, "targetUserId");
        if (targetUserId.isBlank() || expectedRowVersion < 0) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
    }
}
