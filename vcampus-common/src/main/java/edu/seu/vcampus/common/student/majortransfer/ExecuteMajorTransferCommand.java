package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to execute a pending-effective transfer (assign target class and effectuate). */
public record ExecuteMajorTransferCommand(
        String applicationId,
        String targetClassId,
        long expectedVersion
) implements Serializable {
    public ExecuteMajorTransferCommand {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(targetClassId, "targetClassId");
    }
}
