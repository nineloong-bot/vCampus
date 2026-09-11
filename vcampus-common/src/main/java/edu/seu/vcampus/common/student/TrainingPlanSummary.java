package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;

/** Summary view of a training plan for list displays. */
public record TrainingPlanSummary(
        String planId,
        String majorId,
        String majorName,
        String departmentName,
        int enrollmentYear,
        String planName,
        long minElectiveCount,
        BigDecimal minElectiveCredits,
        int courseCount,
        BigDecimal totalRequiredCredits,
        BigDecimal totalElectiveCredits,
        boolean isActive,
        long rowVersion) implements Serializable { }
