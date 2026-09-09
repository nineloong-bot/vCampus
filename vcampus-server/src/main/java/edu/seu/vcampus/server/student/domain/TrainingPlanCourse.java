package edu.seu.vcampus.server.student.domain;

import edu.seu.vcampus.common.student.CourseType;

import java.math.BigDecimal;
import java.time.Instant;

/** Persistence model for a course within a training plan. */
public record TrainingPlanCourse(
        String planCourseId,
        String planId,
        String courseCode,
        String courseName,
        BigDecimal credits,
        CourseType courseType,
        int semester,
        boolean active,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt) { }
