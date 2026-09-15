package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to review a transfer application at the qualification stage. */
/**
 * Carries immutable review major transfer qualification command data.
 * @param applicationId the application identifier
 * @param decision the decision
 * @param comment the comment
 * @param expectedVersion the expected version
 */
public record ReviewMajorTransferQualificationCommand(
        String applicationId,
        MajorTransferDecision decision,
        String comment,
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a review major transfer qualification command.
     * @param applicationId the application identifier
     * @param decision the decision
     * @param comment the comment
     * @param expectedVersion the expected version
     */
    public ReviewMajorTransferQualificationCommand {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(decision, "decision");
    }
}
