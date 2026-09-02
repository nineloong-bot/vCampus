package edu.seu.vcampus.client.course.ui;

import java.time.Instant;
import java.time.LocalDate;

final class CourseFormValidation {
    private CourseFormValidation() { }

    static void requireOrdered(LocalDate start, LocalDate end, String message) {
        if (!end.isAfter(start)) throw new IllegalArgumentException(message);
    }

    static void requireOrdered(Instant start, Instant end, String message) {
        if (!end.isAfter(start)) throw new IllegalArgumentException(message);
    }
}
