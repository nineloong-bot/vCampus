package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;

/** Command to add or update a course within a training plan. */
public record SaveTrainingPlanCourseCommand(
        String planId,
        String planCourseId,
        String courseCode,
        String courseName,
        BigDecimal credits,
        CourseType courseType,
        int semester,
        boolean isActive,
        long expectedVersion) implements Serializable { }
