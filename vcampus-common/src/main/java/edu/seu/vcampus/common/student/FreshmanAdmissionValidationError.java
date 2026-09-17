package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.Objects;

/** A safe, line-specific validation error for a freshman admission CSV. */
public record FreshmanAdmissionValidationError(int lineNumber, String field,
                                                String message) implements Serializable {
    /** Validates a CSV validation error suitable for client display. */
    public FreshmanAdmissionValidationError {
        if (lineNumber < 1) throw new IllegalArgumentException("lineNumber must be positive");
        field = require(field, "field");
        message = require(message, "message");
    }

    private static String require(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }
}
