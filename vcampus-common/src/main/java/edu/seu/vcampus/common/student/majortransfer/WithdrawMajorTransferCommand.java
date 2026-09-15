package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to withdraw a submitted transfer application back to draft. */
/**
 * Carries immutable withdraw major transfer command data.
 * @param applicationId the application identifier
 * @param expectedVersion the expected version
 */
public record WithdrawMajorTransferCommand(
        String applicationId,
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a withdraw major transfer command.
     * @param applicationId the application identifier
     * @param expectedVersion the expected version
     */
    public WithdrawMajorTransferCommand {
        Objects.requireNonNull(applicationId, "applicationId");
    }
}
