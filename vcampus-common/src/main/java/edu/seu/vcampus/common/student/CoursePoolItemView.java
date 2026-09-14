package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;

/** Item in the university-wide course pool. */
public record CoursePoolItemView(
        String courseId,
        String courseCode,
        String courseName,
        String departmentId,
        String departmentName,
        BigDecimal credits,
        int totalHours,
        String description,
        boolean isActive) implements Serializable { }
