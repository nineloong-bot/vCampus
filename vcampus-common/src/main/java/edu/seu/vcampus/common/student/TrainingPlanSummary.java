package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;

/** Summary view of a training plan for list displays. */
/**
 * Carries immutable training plan summary data.
 * @param planId the plan identifier
 * @param majorId the major identifier
 * @param majorName the major name
 * @param departmentName the department name
 * @param enrollmentYear the enrollment year
 * @param planName the plan name
 * @param minElectiveCount the min elective count
 * @param minElectiveCredits the min elective credits
 * @param courseCount the course count
 * @param totalRequiredCredits the total required credits
 * @param totalElectiveCredits the total elective credits
 * @param isActive the is active
 * @param rowVersion the row version
 */
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
