package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.time.LocalDate;

/** Immutable view of a completed transfer execution. */
/**
 * Carries immutable major transfer execution view data.
 * @param executionId the execution identifier
 * @param applicationId the application identifier
 * @param toClassId the to class identifier
 * @param toClassName the to class name
 * @param toMajorId the to major identifier
 * @param toMajorName the to major name
 * @param toDepartmentId the to department identifier
 * @param toDepartmentName the to department name
 * @param courseRecognitionStatus the course recognition status
 * @param operatorUserId the operator user identifier
 * @param effectiveDate the effective date
 */
public record MajorTransferExecutionView(
        String executionId,
        String applicationId,
        String toClassId,
        String toClassName,
        String toMajorId,
        String toMajorName,
        String toDepartmentId,
        String toDepartmentName,
        String courseRecognitionStatus,
        String operatorUserId,
        LocalDate effectiveDate
) implements Serializable { }
