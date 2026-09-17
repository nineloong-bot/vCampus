package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Complete training plan with all courses. */
public record TrainingPlanDetailView(
        String planId,
        String majorId,
        String majorName,
        String departmentName,
        int enrollmentYear,
        String planName,
        long minElectiveCount,
        BigDecimal minElectiveCredits,
        boolean isActive,
        long rowVersion,
        List<TrainingPlanCourseView> courses,
        boolean editable) implements Serializable {

    /**
     * Backward-compatible constructor defaulting {@code editable} to true.
     */
    public TrainingPlanDetailView(
            String planId,
            String majorId,
            String majorName,
            String departmentName,
            int enrollmentYear,
            String planName,
            long minElectiveCount,
            BigDecimal minElectiveCredits,
            boolean isActive,
            long rowVersion,
            List<TrainingPlanCourseView> courses) {
        this(planId, majorId, majorName, departmentName, enrollmentYear, planName,
                minElectiveCount, minElectiveCredits, isActive, rowVersion, courses, true);
    }
}
