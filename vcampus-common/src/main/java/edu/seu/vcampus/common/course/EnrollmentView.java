package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/** Serializable enrollment result returned to course clients. */
public record EnrollmentView(
        String enrollmentId,
        String offeringId,
        String studentId,
        String enrollmentType,
        String enrollmentStatus,
        Instant enrolledAt,
        Instant droppedAt,
        long rowVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
