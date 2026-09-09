package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to finalize (approve) a proposed transfer application. */
public record FinalizeMajorTransferCommand(
        String applicationId,
        long expectedVersion
) implements Serializable {
    public FinalizeMajorTransferCommand {
        Objects.requireNonNull(applicationId, "applicationId");
    }
}
