package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to review a transfer application at the qualification stage. */
public record ReviewMajorTransferQualificationCommand(
        String applicationId,
        MajorTransferDecision decision,
        String comment,
        long expectedVersion
) implements Serializable {
    public ReviewMajorTransferQualificationCommand {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(decision, "decision");
    }
}
