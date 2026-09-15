package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Explains whether a student may select a course as a retake. */
/**
 * Carries immutable retake eligibility data.
 * @param courseId the course identifier
 * @param eligible the eligible
 * @param failedAttemptIds the failed attempt identifiers
 * @param reason the reason
 */
public record RetakeEligibility(String courseId, boolean eligible,
                                List<String> failedAttemptIds, String reason)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /**
     * Validates and creates a retake eligibility.
     * @param courseId the course id
     * @param eligible the eligible
     * @param failedAttemptIds the failed attempt ids
     * @param reason the reason
     */
    public RetakeEligibility {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(failedAttemptIds, "failedAttemptIds");
        failedAttemptIds = List.copyOf(failedAttemptIds);
    }
}
