package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Command to create or update a training plan. */
/**
 * Carries immutable save training plan command data.
 * @param planId the plan identifier
 * @param majorId the major identifier
 * @param enrollmentYear the enrollment year
 * @param planName the plan name
 * @param minElectiveCount the min elective count
 * @param minElectiveCredits the min elective credits
 * @param isActive the is active
 * @param expectedVersion the expected version
 */
public record SaveTrainingPlanCommand(
        String planId,
        String majorId,
        int enrollmentYear,
        String planName,
        long minElectiveCount,
        java.math.BigDecimal minElectiveCredits,
        boolean isActive,
        long expectedVersion) implements Serializable { }
