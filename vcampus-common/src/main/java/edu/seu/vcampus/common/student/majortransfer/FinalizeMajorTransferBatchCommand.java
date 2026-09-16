package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Requests atomic finalization and immediate effect for one closed transfer batch. */
public record FinalizeMajorTransferBatchCommand(String batchId, long expectedVersion)
        implements Serializable {
    /** Validates the stable batch identifier and optimistic version. */
    public FinalizeMajorTransferBatchCommand {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("batchId");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion");
    }
}
