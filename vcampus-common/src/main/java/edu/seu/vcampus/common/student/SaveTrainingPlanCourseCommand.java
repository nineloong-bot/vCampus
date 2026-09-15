package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;

/** Command to add or update a course within a training plan. */
/**
 * Carries immutable save training plan course command data.
 * @param planId the plan identifier
 * @param planCourseId the plan course identifier
 * @param courseCode the course code
 * @param courseName the course name
 * @param credits the credits
 * @param courseType the course type
 * @param semester the semester
 * @param isActive the is active
 * @param expectedVersion the expected version
 * @param courseId the course identifier
 * @param offeringDepartmentId the offering department identifier
 * @param offeringDepartmentName the offering department name
 * @param allocatedQuota the allocated quota
 */
public record SaveTrainingPlanCourseCommand(
        String planId,
        String planCourseId,
        String courseCode,
        String courseName,
        BigDecimal credits,
        CourseType courseType,
        int semester,
        boolean isActive,
        long expectedVersion,
        String courseId,
        String offeringDepartmentId,
        String offeringDepartmentName,
        Integer allocatedQuota) implements Serializable {

    /**
     * Validates and creates a save training plan course command.
     * @param planId the plan identifier
     * @param planCourseId the plan course identifier
     * @param courseCode the course code
     * @param courseName the course name
     * @param credits the credits
     * @param courseType the course type
     * @param semester the semester
     * @param isActive the is active
     * @param expectedVersion the expected version
     */
    public SaveTrainingPlanCourseCommand(
            String planId,
            String planCourseId,
            String courseCode,
            String courseName,
            BigDecimal credits,
            CourseType courseType,
            int semester,
            boolean isActive,
            long expectedVersion) {
        this(planId, planCourseId, courseCode, courseName, credits, courseType, semester, isActive, expectedVersion,
                null, null, null, null);
    }
}
