package edu.seu.vcampus.server.course.service;

import java.util.Objects;

/** Minimal student data required to decide course enrollment and curriculum eligibility. */
public record StudentEnrollmentEligibility(String studentId, String status,
                                           String majorCode, int cohortYear) {
    /** Requires the upstream student adapter to return both stable fields. */
    public StudentEnrollmentEligibility {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(status, "status");
        if (majorCode != null && majorCode.isBlank()) throw new IllegalArgumentException("majorCode");
        if (cohortYear != 0 && cohortYear < 2000) throw new IllegalArgumentException("cohortYear");
    }

    /** Compatibility projection for deployments whose student module has not added curriculum fields yet. */
    public StudentEnrollmentEligibility(String studentId, String status) {
        this(studentId, status, null, 0);
    }

    public boolean hasCurriculumContext() { return majorCode != null && cohortYear >= 2000; }
}
