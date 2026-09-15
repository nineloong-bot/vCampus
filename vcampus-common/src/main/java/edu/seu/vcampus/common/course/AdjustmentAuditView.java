package edu.seu.vcampus.common.course; import java.io.*; import java.time.Instant;
/** Safe immutable enrollment-adjustment audit row. */ /**
 * Carries immutable adjustment audit view data.
 * @param adjustmentId the adjustment identifier
 * @param studentId the student identifier
 * @param adjustmentType the adjustment type
 * @param sourceOfferingId the source offering identifier
 * @param targetOfferingId the target offering identifier
 * @param operationResult the operation result
 * @param failureCode the failure code
 * @param operatedAt the operated at
 */
public record AdjustmentAuditView(String adjustmentId,String studentId,String adjustmentType,String sourceOfferingId,String targetOfferingId,String operationResult,String failureCode,Instant operatedAt)implements Serializable{@Serial private static final long serialVersionUID=1L;}
