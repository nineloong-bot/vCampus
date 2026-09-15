package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to review a transfer application at the source-college stage. */
/**
 * Carries immutable review major transfer source command data.
 * @param applicationId the application identifier
 * @param decision the decision
 * @param sourceVerified the source verified
 * @param noMisconduct the no misconduct
 * @param admissionAllowed the admission allowed
 * @param comment the comment
 * @param expectedVersion the expected version
 */
public record ReviewMajorTransferSourceCommand(
        String applicationId,
        MajorTransferDecision decision,
        boolean sourceVerified,
        boolean noMisconduct,
        boolean admissionAllowed,
        String comment,
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a review major transfer source command.
     * @param applicationId the application identifier
     * @param decision the decision
     * @param sourceVerified the source verified
     * @param noMisconduct the no misconduct
     * @param admissionAllowed the admission allowed
     * @param comment the comment
     * @param expectedVersion the expected version
     */
    public ReviewMajorTransferSourceCommand {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(decision, "decision");
    }
}
