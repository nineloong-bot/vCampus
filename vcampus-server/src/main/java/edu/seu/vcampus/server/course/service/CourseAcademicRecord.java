package edu.seu.vcampus.server.course.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable snapshot of the grades used by one course-selection decision. */
public record CourseAcademicRecord(List<CourseAcademicResult> results) {
    /** Copies the supplied student-owned grade results into a stable decision snapshot. */
    public CourseAcademicRecord {
        results = List.copyOf(Objects.requireNonNull(results, "results"));
    }

    /** Returns whether the canonical course has a passing result. */
    public boolean hasPassed(String courseCode) {
        String code = normalize(courseCode);
        return results.stream().anyMatch(result -> code.equals(result.courseCode())
                && "PASSED".equals(result.outcome()));
    }

    /** Returns whether the canonical course failed and has not subsequently passed. */
    public boolean requiresRetake(String courseCode) {
        String code = normalize(courseCode);
        return !hasPassed(code) && results.stream().anyMatch(result -> code.equals(result.courseCode())
                && "FAILED".equals(result.outcome()));
    }

    /** Returns stable student-grade identifiers for failed results of the canonical course. */
    public List<String> failedRecordIds(String courseCode) {
        String code = normalize(courseCode);
        return results.stream().filter(result -> code.equals(result.courseCode())
                        && "FAILED".equals(result.outcome()))
                .map(CourseAcademicResult::recordId).toList();
    }

    private static String normalize(String courseCode) {
        return Objects.requireNonNull(courseCode, "courseCode").trim().toUpperCase(Locale.ROOT);
    }
}
