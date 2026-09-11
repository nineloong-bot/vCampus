package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Command to create or update a training plan. */
public record SaveTrainingPlanCommand(
        String planId,
        String majorId,
        int enrollmentYear,
        String planName,
        long minElectiveCount,
        java.math.BigDecimal minElectiveCredits,
        boolean isActive,
        long expectedVersion) implements Serializable { }
