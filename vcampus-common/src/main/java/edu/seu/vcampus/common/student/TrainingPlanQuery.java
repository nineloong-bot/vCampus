package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Query for listing training plans with optional filters. */
/**
 * Carries immutable training plan query data.
 * @param majorId the major identifier
 * @param enrollmentYear the enrollment year
 * @param page the page
 * @param pageSize the page size
 */
public record TrainingPlanQuery(
        String majorId,
        Integer enrollmentYear,
        int page,
        int pageSize) implements Serializable { }
