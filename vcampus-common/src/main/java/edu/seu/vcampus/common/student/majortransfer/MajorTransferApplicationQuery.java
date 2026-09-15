package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Query to list applications for admin review. */
/**
 * Carries immutable major transfer application query data.
 * @param batchId the batch identifier
 * @param optionId the option identifier
 * @param status the status
 */
public record MajorTransferApplicationQuery(
        String batchId,
        String optionId,
        MajorTransferStatus status
) implements Serializable { }
