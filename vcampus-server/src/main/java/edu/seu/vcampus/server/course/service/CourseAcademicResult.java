package edu.seu.vcampus.server.course.service;

import java.util.Locale;
import java.util.Objects;

/** Read-only projection of one canonical result owned by the student module. */
public record CourseAcademicResult(String recordId, String courseCode, String outcome) {
    /** Creates a normalized result without exposing student-module DTOs to course services. */
    public CourseAcademicResult {
        recordId = requireText(recordId, "recordId");
        courseCode = requireText(courseCode, "courseCode").toUpperCase(Locale.ROOT);
        outcome = requireText(outcome, "outcome").toUpperCase(Locale.ROOT);
        if (!"PASSED".equals(outcome) && !"FAILED".equals(outcome)) {
            throw new IllegalArgumentException("outcome");
        }
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name);
        return normalized;
    }
}
