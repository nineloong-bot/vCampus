package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to save or update a student's transfer application draft. */
/**
 * Carries immutable save major transfer draft command data.
 * @param applicationId the application identifier
 * @param batchId the batch identifier
 * @param optionId the option identifier
 * @param applicationType the application type
 * @param reason the reason
 * @param expectedVersion the expected version
 */
public record SaveMajorTransferDraftCommand(
        String applicationId,
        String batchId,
        String optionId,
        MajorTransferApplicationType applicationType,
        String reason,
        long expectedVersion
) implements Serializable {
    /**
     * Validates and creates a save major transfer draft command.
     * @param applicationId the application id
     * @param batchId the batch id
     * @param optionId the option id
     * @param applicationType the application type
     * @param reason the reason
     * @param expectedVersion the expected version
     */
    public SaveMajorTransferDraftCommand {
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(optionId, "optionId");
        Objects.requireNonNull(applicationType, "applicationType");
        if (reason != null && reason.length() > 2000) throw new IllegalArgumentException("申请理由最多2000字");
    }
}
