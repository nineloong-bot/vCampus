package edu.seu.vcampus.server.course.repository;

import java.math.BigDecimal;
import java.time.Instant;

/** Persisted course-catalog entry. */
public record Course(String courseId, String courseCode, String courseName, BigDecimal credit,
                     int totalHours, String description, boolean active, long rowVersion,
                     Instant createdAt, Instant updatedAt) {
}
