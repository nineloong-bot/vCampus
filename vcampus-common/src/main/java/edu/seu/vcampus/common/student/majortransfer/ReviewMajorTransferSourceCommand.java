package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to review a transfer application at the source-college stage. */
public record ReviewMajorTransferSourceCommand(
        String applicationId,
        MajorTransferDecision decision,
        boolean sourceVerified,
        boolean noMisconduct,
        boolean admissionAllowed,
        String comment,
        long expectedVersion
) implements Serializable {
    public ReviewMajorTransferSourceCommand {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(decision, "decision");
    }
}
