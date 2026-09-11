package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.Objects;

/** Command to save or update a student's transfer application draft. */
public record SaveMajorTransferDraftCommand(
        String applicationId,
        String batchId,
        String optionId,
        MajorTransferApplicationType applicationType,
        String reason,
        long expectedVersion
) implements Serializable {
    public SaveMajorTransferDraftCommand {
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(optionId, "optionId");
        Objects.requireNonNull(applicationType, "applicationType");
        if (reason != null && reason.length() > 2000) throw new IllegalArgumentException("申请理由最多2000字");
    }
}
