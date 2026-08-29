package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Requests an atomic optimistic-lock protected move to another teaching offering. */
public record ChangeOfferingCommand(String sourceEnrollmentId, String targetOfferingId,
                                    long expectedVersion) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public ChangeOfferingCommand {
        Objects.requireNonNull(sourceEnrollmentId, "sourceEnrollmentId");
        Objects.requireNonNull(targetOfferingId, "targetOfferingId");
        CourseValidation.text("sourceEnrollmentId", sourceEnrollmentId, 36);
        CourseValidation.text("targetOfferingId", targetOfferingId, 36);
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
    }
}
