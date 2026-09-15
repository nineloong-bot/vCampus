package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;

/** A single course entry within a training plan. */
/**
 * Carries immutable training plan course view data.
 * @param planCourseId the plan course identifier
 * @param courseCode the course code
 * @param courseName the course name
 * @param credits the credits
 * @param courseType the course type
 * @param semester the semester
 * @param isActive the is active
 * @param rowVersion the row version
 * @param courseId the course identifier
 * @param offeringDepartmentId the offering department identifier
 * @param offeringDepartmentName the offering department name
 * @param allocatedQuota the allocated quota
 */
public record TrainingPlanCourseView(
        String planCourseId,
        String courseCode,
        String courseName,
        BigDecimal credits,
        CourseType courseType,
        int semester,
        boolean isActive,
        long rowVersion,
        String courseId,
        String offeringDepartmentId,
        String offeringDepartmentName,
        Integer allocatedQuota) implements Serializable {

    /**
     * Validates and creates a training plan course view.
     * @param planCourseId the plan course identifier
     * @param courseCode the course code
     * @param courseName the course name
     * @param credits the credits
     * @param courseType the course type
     * @param semester the semester
     * @param isActive the is active
     * @param rowVersion the row version
     */
    public TrainingPlanCourseView(
            String planCourseId,
            String courseCode,
            String courseName,
            BigDecimal credits,
            CourseType courseType,
            int semester,
            boolean isActive,
            long rowVersion) {
        this(planCourseId, courseCode, courseName, credits, courseType, semester, isActive, rowVersion,
                null, null, null, null);
    }
}
