package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command for the target college to give final approval to an assessed application. */
public record FinalizeMajorTransferCommand(
        String applicationId,
        long expectedVersion
) implements Serializable {
    public FinalizeMajorTransferCommand {
        Objects.requireNonNull(applicationId, "applicationId");
    }
}
