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

    /**
     * Creates a training plan course with its required collaborators.
     * @param planCourseId the plan course identifier
     * @param planId the plan identifier
     * @param courseCode the course code
     * @param courseName the course name
     * @param credits the credits
     * @param courseType the course type
     * @param semester the semester
     * @param active the active
     * @param rowVersion the row version
     * @param createdAt the created at
     * @param updatedAt the updated at
     */
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
        this(planCourseId, planId, courseCode, courseName, credits, courseType, semester, active,
                rowVersion, createdAt, updatedAt, null, null, null, null);
    }
}
