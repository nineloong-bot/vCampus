package edu.seu.vcampus.server.student.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Persistence model for a training plan. */
public record TrainingPlan(
        String planId,
        String majorId,
        int enrollmentYear,
        String planName,
        long minElectiveCount,
        BigDecimal minElectiveCredits,
        boolean active,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt) { }
