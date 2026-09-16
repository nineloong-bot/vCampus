package edu.seu.vcampus.server.course.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseAcademicRecordTest {
    @Test
    void passedResultOverridesEarlierFailureForTheSameCanonicalCourse() {
        CourseAcademicRecord record = new CourseAcademicRecord(List.of(
                new CourseAcademicResult("failed-grade", "CS101", "FAILED"),
                new CourseAcademicResult("passed-grade", "CS101", "PASSED")));

        assertThat(record.hasPassed("CS101")).isTrue();
        assertThat(record.requiresRetake("CS101")).isFalse();
        assertThat(record.failedRecordIds("CS101")).containsExactly("failed-grade");
    }

    @Test
    void failedResultRequiresRetakeAndMissingResultDoesNot() {
        CourseAcademicRecord record = new CourseAcademicRecord(List.of(
                new CourseAcademicResult("grade-1", "CS102", "FAILED")));

        assertThat(record.requiresRetake("cs102")).isTrue();
        assertThat(record.hasPassed("CS102")).isFalse();
        assertThat(record.requiresRetake("CS999")).isFalse();
    }
}
