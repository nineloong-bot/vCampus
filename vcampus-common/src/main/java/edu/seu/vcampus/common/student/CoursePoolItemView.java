package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;

/** Item in the university-wide course pool. */
/**
 * Carries immutable course pool item view data.
 * @param courseId the course identifier
 * @param courseCode the course code
 * @param courseName the course name
 * @param departmentId the department identifier
 * @param departmentName the department name
 * @param credits the credits
 * @param totalHours the total hours
 * @param description the description
 * @param isActive the is active
 */
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
