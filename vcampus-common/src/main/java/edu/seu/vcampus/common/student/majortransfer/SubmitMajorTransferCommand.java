package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to submit a draft transfer application. */
public record SubmitMajorTransferCommand(
        String applicationId,
        long expectedVersion
) implements Serializable {
    public SubmitMajorTransferCommand {
        Objects.requireNonNull(applicationId, "applicationId");
    }
}
