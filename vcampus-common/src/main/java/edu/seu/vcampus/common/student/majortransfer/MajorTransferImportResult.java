package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** Result of a bulk score import operation. */
/**
 * Carries immutable major transfer import result data.
 * @param totalEntries the total entries
 * @param successCount the success count
 * @param failureCount the failure count
 * @param failures the failures
 */
public record MajorTransferImportResult(
        int totalEntries,
        int successCount,
        int failureCount,
        List<Failure> failures
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
 * Carries immutable failure data.
 * @param applicationId the application identifier
 * @param reason the reason
 */
public record Failure(
            String applicationId,
            String reason
    ) implements Serializable { }
}
