package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to execute a pending-effective transfer (assign target class and effectuate). */
/**
 * Carries immutable execute major transfer command data.
 * @param applicationId the application identifier
 * @param targetClassId the target class identifier
 * @param expectedVersion the expected version
 */
public record ExecuteMajorTransferCommand(
        String applicationId,
        String targetClassId,
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a execute major transfer command.
     * @param applicationId the application identifier
     * @param targetClassId the target class identifier
     * @param expectedVersion the expected version
     */
    public ExecuteMajorTransferCommand {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(targetClassId, "targetClassId");
    }
}
