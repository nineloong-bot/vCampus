package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Requests an optimistic-lock protected drop of a retained enrollment. */
public record DropCommand(String enrollmentId, long expectedVersion) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public DropCommand {
        Objects.requireNonNull(enrollmentId, "enrollmentId");
        CourseValidation.text("enrollmentId", enrollmentId, 36);
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
    }
}
