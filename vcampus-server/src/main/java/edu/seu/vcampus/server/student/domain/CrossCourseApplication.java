package edu.seu.vcampus.server.student.domain;

import edu.seu.vcampus.common.student.CrossCourseApplicationStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Persistence model for a cross-disciplinary course application. */
public record CrossCourseApplication(
        String applicationId,
        String courseId,
        String courseCode,
        String courseName,
        BigDecimal credits,
        String offeringDepartmentId,
        String offeringDepartmentName,
        String targetDepartmentId,
        String targetDepartmentName,
        String targetPlanId,
        String targetPlanName,
        int semester,
        int requestedQuota,
        Integer allocatedQuota,
        String applicantUserId,
        String applicantName,
        String reason,
        CrossCourseApplicationStatus status,
        String reviewerUserId,
        String reviewComment,
        Instant reviewedAt,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt) { }
