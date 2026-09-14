package edu.seu.vcampus.server.student.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Persistence model for a course in the university-wide course pool. */
public record CoursePoolItem(
        String courseId,
        String courseCode,
        String courseName,
        String departmentId,
        String departmentName,
        BigDecimal credit,
        int totalHours,
        String description,
        boolean active,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt) { }
