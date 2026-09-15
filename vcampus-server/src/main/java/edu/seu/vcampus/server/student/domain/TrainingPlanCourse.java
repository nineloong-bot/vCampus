package edu.seu.vcampus.server.student.domain;

import edu.seu.vcampus.common.student.CourseType;

import java.math.BigDecimal;
import java.time.Instant;

/** Persistence model for a course within a training plan. */
public record TrainingPlanCourse(
        String planCourseId,
        String planId,
        String courseCode,
        String courseName,
        BigDecimal credits,
        int totalHours,
        CourseType courseType,
        int semester,
        boolean active,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt,
        String courseId,
        String offeringDepartmentId,
        String offeringDepartmentName,
        Integer allocatedQuota) {

    public TrainingPlanCourse(
            String planCourseId,
            String planId,
            String courseCode,
            String courseName,
            BigDecimal credits,
            CourseType courseType,
            int semester,
            boolean active,
            long rowVersion,
            Instant createdAt,
            Instant updatedAt) {
        this(planCourseId, planId, courseCode, courseName, credits, 0, courseType, semester, active,
                rowVersion, createdAt, updatedAt, null, null, null, null);
    }
}
