package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** Requests the one-time effectuation of a batch already given final approval. */
public record EffectiveMajorTransferBatchCommand(String batchId, long expectedVersion)
        implements Serializable {
    /** Validates the batch identifier and optimistic version. */
    public EffectiveMajorTransferBatchCommand {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("batchId");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion");
    }
}
