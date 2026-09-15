package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Requests an optimistic-lock protected drop of a retained enrollment. */
/**
 * Carries immutable drop command data.
 * @param enrollmentId the enrollment identifier
 * @param expectedVersion the expected version
 */
public record DropCommand(String enrollmentId, long expectedVersion) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /**
     * Validates and creates a drop command.
     * @param enrollmentId the enrollment id
     * @param expectedVersion the expected version
     */
    public DropCommand {
        Objects.requireNonNull(enrollmentId, "enrollmentId");
        CourseValidation.text("enrollmentId", enrollmentId, 36);
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
    }
}
