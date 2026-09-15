package edu.seu.vcampus.server.student.domain;

import java.util.Objects;

/** Department metadata owned by the student module. */
public record Department(String departmentId, String departmentCode,
                         String departmentName, boolean active, long rowVersion) {
    /**
     * Creates a department with its required collaborators.
     * @param departmentId the department identifier
     * @param departmentCode the department code
     * @param departmentName the department name
     * @param active the active
     * @param rowVersion the row version
     */
    public Department {
        requireText(departmentId, "departmentId");
        requireText(departmentCode, "departmentCode");
        requireText(departmentName, "departmentName");
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
