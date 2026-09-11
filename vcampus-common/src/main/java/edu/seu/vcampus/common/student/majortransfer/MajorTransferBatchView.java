package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.time.Instant;

/** Immutable view of a major-transfer batch. */
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
