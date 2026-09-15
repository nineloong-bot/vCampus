package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;

/** Command to add or update a course within a training plan. */
public record SaveTrainingPlanCourseCommand(
        String planId,
        String planCourseId,
        String courseCode,
        String courseName,
        BigDecimal credits,
        int totalHours,
        CourseType courseType,
        int semester,
        boolean isActive,
        long expectedVersion,
        String courseId,
        String offeringDepartmentId,
        String offeringDepartmentName,
        Integer allocatedQuota) implements Serializable {

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
        this(planId, planCourseId, courseCode, courseName, credits, 0, courseType, semester, isActive, expectedVersion,
                null, null, null, null);
    }
}
