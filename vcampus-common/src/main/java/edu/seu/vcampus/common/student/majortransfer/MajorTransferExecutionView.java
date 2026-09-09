package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.time.LocalDate;

/** Immutable view of a completed transfer execution. */
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
