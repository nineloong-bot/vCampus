package edu.seu.vcampus.server.course.service;

import java.util.Objects;

/** Minimal student data required to decide course enrollment eligibility. */
public record StudentEnrollmentEligibility(String studentId, String status) {
    /** Requires the upstream student adapter to return both stable fields. */
    public StudentEnrollmentEligibility {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(status, "status");
    }
}
