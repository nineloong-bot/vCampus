package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** Result of a bulk score import operation. */
public record MajorTransferImportResult(
        int totalEntries,
        int successCount,
        int failureCount,
        List<Failure> failures
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public record Failure(
            String applicationId,
            String reason
    ) implements Serializable { }
}
