package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to submit a draft transfer application. */
/**
 * Carries immutable submit major transfer command data.
 * @param applicationId the application identifier
 * @param expectedVersion the expected version
 */
public record SubmitMajorTransferCommand(
        String applicationId,
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a submit major transfer command.
     * @param applicationId the application identifier
     * @param expectedVersion the expected version
     */
    public SubmitMajorTransferCommand {
        Objects.requireNonNull(applicationId, "applicationId");
    }
}
