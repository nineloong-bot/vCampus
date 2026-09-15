package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Minimal score-free course projection published to other server modules. */
/**
 * Carries immutable course summary data.
 * @param courseId the course identifier
 * @param courseCode the course code
 * @param courseName the course name
 */
public record CourseSummary(String courseId, String courseCode, String courseName) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /**
     * Validates and creates a course summary.
     * @param courseId the course id
     * @param courseCode the course code
     * @param courseName the course name
     */
    public CourseSummary {
        Objects.requireNonNull(courseId);
        Objects.requireNonNull(courseCode);
        Objects.requireNonNull(courseName);
        if (courseId.isBlank() || courseCode.isBlank() || courseName.isBlank()) {
            throw new IllegalArgumentException("invalid course summary");
        }
    }
}
