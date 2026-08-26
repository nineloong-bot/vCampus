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
        if (sourceEnrollmentId.isBlank() || targetOfferingId.isBlank()) {
            throw new IllegalArgumentException("offering and enrollment identifiers must not be blank");
        }
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
    }
}
