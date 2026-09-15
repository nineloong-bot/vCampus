package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Safe immutable enrollment-adjustment audit row with human-readable identifiers.
 *
 * @param adjustmentId audit identifier
 * @param studentNumber stable student number
 * @param adjustmentType operation type
 * @param sourceOfferingDisplay readable source teaching class
 * @param targetOfferingDisplay readable target teaching class
 * @param operationResult operation result
 * @param failureCode safe failure code
 * @param operatedAt operation time
 */
public record AdjustmentAuditView(String adjustmentId, String studentNumber,
                                  String adjustmentType, String sourceOfferingDisplay,
                                  String targetOfferingDisplay, String operationResult,
                                  String failureCode, Instant operatedAt) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
