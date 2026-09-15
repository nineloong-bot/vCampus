package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Complete training plan with all courses. */
/**
 * Carries immutable training plan detail view data.
 * @param planId the plan identifier
 * @param majorId the major identifier
 * @param majorName the major name
 * @param departmentName the department name
 * @param enrollmentYear the enrollment year
 * @param planName the plan name
 * @param minElectiveCount the min elective count
 * @param minElectiveCredits the min elective credits
 * @param isActive the is active
 * @param rowVersion the row version
 * @param courses the courses
 */
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
