package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.time.Instant;

/** Immutable view of a major-transfer batch. */
/**
 * Carries immutable major transfer batch view data.
 * @param batchId the batch identifier
 * @param batchName the batch name
 * @param status the status
 * @param applicationStart the application start
 * @param applicationEnd the application end
 * @param publicityStart the publicity start
 * @param publicityEnd the publicity end
 * @param effectiveDate the effective date
 * @param rowVersion the row version
 */
public record MajorTransferBatchView(
        String batchId,
        String batchName,
        MajorTransferBatchStatus status,
        Instant applicationStart,
        Instant applicationEnd,
        Instant publicityStart,
        Instant publicityEnd,
        Instant effectiveDate,
        long rowVersion
) implements Serializable { }
