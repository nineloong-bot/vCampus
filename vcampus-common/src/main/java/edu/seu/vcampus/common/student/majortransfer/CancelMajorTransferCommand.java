package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to cancel a transfer application (admin only). */
public record CancelMajorTransferCommand(
        String applicationId,
        String reason,
        long expectedVersion
) implements Serializable {
    public CancelMajorTransferCommand {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) throw new IllegalArgumentException("取消原因不能为空");
    }
}
