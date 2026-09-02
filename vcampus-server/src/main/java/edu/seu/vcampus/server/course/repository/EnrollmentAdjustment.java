package edu.seu.vcampus.server.course.repository;

import java.time.Instant;

/** Immutable audit row for an add, drop, or offering change attempt. */
public record EnrollmentAdjustment(String adjustmentId, String studentId, String adjustmentType,
                                   String sourceOfferingId, String targetOfferingId,
                                   String operationResult, String failureCode, Instant operatedAt) {
}
