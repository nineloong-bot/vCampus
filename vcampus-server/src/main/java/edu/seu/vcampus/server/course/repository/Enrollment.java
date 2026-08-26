package edu.seu.vcampus.server.course.repository;

import java.time.Instant;

/** Persisted student enrollment, retained when dropped so it can be reactivated. */
public record Enrollment(String enrollmentId, String offeringId, String studentId,
                         String enrollmentType, String enrollmentStatus, Instant enrolledAt,
                         Instant droppedAt, long rowVersion, Instant createdAt, Instant updatedAt) {
}
