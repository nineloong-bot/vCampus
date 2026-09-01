package edu.seu.vcampus.server.student.domain;

import java.util.Locale;

/** Major metadata including the three-character student-number prefix. */
public record Major(String majorId, String departmentId, String majorCode,
                    String majorName, boolean active, long rowVersion) {
    public Major {
        requireText(majorId, "majorId");
        requireText(departmentId, "departmentId");
        requireText(majorName, "majorName");
        majorCode = majorCode == null ? null : majorCode.toUpperCase(Locale.ROOT);
        if (majorCode == null || !majorCode.matches("[0-9A-Z]{3}")) {
            throw new IllegalArgumentException("majorCode must match ^[0-9A-Z]{3}$");
        }
        if (rowVersion < 0) {
            throw new IllegalArgumentException("rowVersion must not be negative");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
