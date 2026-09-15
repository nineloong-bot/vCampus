package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command for the target college to give final approval to an assessed application. */
/**
 * Carries immutable finalize major transfer command data.
 * @param applicationId the application identifier
 * @param expectedVersion the expected version
 */
public record FinalizeMajorTransferCommand(
        String applicationId,
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a finalize major transfer command.
     * @param applicationId the application identifier
     * @param expectedVersion the expected version
     */
    public FinalizeMajorTransferCommand {
        Objects.requireNonNull(applicationId, "applicationId");
    }
}
