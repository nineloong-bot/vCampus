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
        List<TrainingPlanCourseView> courses) implements Serializable { }
