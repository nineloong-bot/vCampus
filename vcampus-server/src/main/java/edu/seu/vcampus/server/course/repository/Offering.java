package edu.seu.vcampus.server.course.repository;

import java.time.Instant;

/** Persisted teaching offering for one course in one term. */
public record Offering(String offeringId, String termId, String courseId, String teacherUserId,
                       String className, int capacity, int enrolledCount, String offeringStatus,
                       long rowVersion, Instant createdAt, Instant updatedAt) {
}
