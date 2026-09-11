package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;

/** A single course entry within a training plan. */
public record TrainingPlanCourseView(
        String planCourseId,
        String courseCode,
        String courseName,
        BigDecimal credits,
        CourseType courseType,
        int semester,
        boolean isActive,
        long rowVersion) implements Serializable { }
