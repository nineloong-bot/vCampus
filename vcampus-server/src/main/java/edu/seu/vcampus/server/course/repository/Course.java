package edu.seu.vcampus.server.course.repository;

import java.math.BigDecimal;
import java.time.Instant;

/** Persisted course-catalog entry. */
public record Course(String courseId, String courseCode, String courseName,
                     String departmentId, String departmentName, BigDecimal credit,
                     int totalHours, String description, boolean active, long rowVersion,
                     Instant createdAt, Instant updatedAt) {
    /** Compatibility constructor for catalog entries without an assigned department. */
    public Course(String courseId, String courseCode, String courseName, BigDecimal credit,
                  int totalHours, String description, boolean active, long rowVersion,
                  Instant createdAt, Instant updatedAt) {
        this(courseId, courseCode, courseName, null, null, credit, totalHours, description,
                active, rowVersion, createdAt, updatedAt);
    }
}
