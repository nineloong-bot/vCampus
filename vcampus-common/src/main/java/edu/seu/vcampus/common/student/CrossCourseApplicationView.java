package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/** View of a cross-disciplinary course application. */
public record CrossCourseApplicationView(
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
        Instant createdAt,
        Instant updatedAt) implements Serializable { }
