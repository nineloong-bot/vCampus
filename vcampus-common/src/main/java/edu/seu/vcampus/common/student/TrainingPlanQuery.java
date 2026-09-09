package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Query for listing training plans with optional filters. */
public record TrainingPlanQuery(
        String majorId,
        Integer enrollmentYear,
        int page,
        int pageSize) implements Serializable { }
