package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Requests administrator-controlled initialization of a teacher password.
 * This contract carries only the target teacher and optimistic-lock version;
 * it never carries plaintext password material.
 */
/**
 * Carries immutable reset teacher password command data.
 * @param targetUserId the target user identifier
 * @param expectedRowVersion the expected row version
 */
public record ResetTeacherPasswordCommand(
        String targetUserId,
        long expectedRowVersion
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Validates the target identity and optimistic-lock version. */
    public ResetTeacherPasswordCommand {
        Objects.requireNonNull(targetUserId, "targetUserId");
        if (targetUserId.isBlank() || expectedRowVersion < 0) {
            throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        }
    }
}
