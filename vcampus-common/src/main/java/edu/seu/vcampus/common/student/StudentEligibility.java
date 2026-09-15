package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * Carries immutable student eligibility data.
 * @param studentId the student identifier
 * @param status the status
 * @param eligible the eligible
 * @param reason the reason
 * @param majorCode the major code
 * @param cohortYear the cohort year
 */
public record StudentEligibility(String studentId, StudentStatus status,
        boolean eligible, String reason, String majorCode, int cohortYear)
        implements Serializable {
    /**
     * Validates and creates a student eligibility.
     * @param studentId the student identifier
     * @param status the status
     * @param eligible the eligible
     * @param reason the reason
     */
    public StudentEligibility(String studentId, StudentStatus status,
            boolean eligible, String reason) {
        this(studentId, status, eligible, reason, null, 0);
    }
}
