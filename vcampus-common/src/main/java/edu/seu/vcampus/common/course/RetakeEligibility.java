package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Explains whether a student may select a course as a retake. */
public record RetakeEligibility(String courseId, boolean eligible,
                                List<String> failedAttemptIds, String reason)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    public RetakeEligibility {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(failedAttemptIds, "failedAttemptIds");
        failedAttemptIds = List.copyOf(failedAttemptIds);
    }
}
