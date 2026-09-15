package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to cancel a transfer application (admin only). */
/**
 * Carries immutable cancel major transfer command data.
 * @param applicationId the application identifier
 * @param reason the reason
 * @param expectedVersion the expected version
 */
public record CancelMajorTransferCommand(
        String applicationId,
        String reason,
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a cancel major transfer command.
     * @param applicationId the application id
     * @param reason the reason
     * @param expectedVersion the expected version
     */
    public CancelMajorTransferCommand {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) throw new IllegalArgumentException("取消原因不能为空");
    }
}
