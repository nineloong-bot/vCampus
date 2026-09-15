package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/** View of a cross-disciplinary course application. */
/**
 * Carries immutable cross course application view data.
 * @param applicationId the application identifier
 * @param courseId the course identifier
 * @param courseCode the course code
 * @param courseName the course name
 * @param credits the credits
 * @param offeringDepartmentId the offering department identifier
 * @param offeringDepartmentName the offering department name
 * @param targetDepartmentId the target department identifier
 * @param targetDepartmentName the target department name
 * @param targetPlanId the target plan identifier
 * @param targetPlanName the target plan name
 * @param semester the semester
 * @param requestedQuota the requested quota
 * @param allocatedQuota the allocated quota
 * @param applicantUserId the applicant user identifier
 * @param applicantName the applicant name
 * @param reason the reason
 * @param status the status
 * @param reviewerUserId the reviewer user identifier
 * @param reviewComment the review comment
 * @param reviewedAt the reviewed at
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
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
