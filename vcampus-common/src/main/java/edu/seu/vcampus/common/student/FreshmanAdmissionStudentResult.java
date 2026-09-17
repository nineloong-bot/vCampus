package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.Objects;

/** Safe final admission details for one freshman CSV row. */
public record FreshmanAdmissionStudentResult(int lineNumber, String studentNumber,
                                             String className) implements Serializable {
    /** Validates a committed freshman admission row result. */
    public FreshmanAdmissionStudentResult {
        if (lineNumber < 2) throw new IllegalArgumentException("invalid lineNumber");
        studentNumber = require(studentNumber, "studentNumber");
        className = require(className, "className");
    }

    private static String require(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }
}
