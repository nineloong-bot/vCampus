package edu.seu.vcampus.server.student.domain;

/** A class within one major and enrollment year. */
public record StudentClass(String classId, String majorId, String classCode,
                           String className, int enrollmentYear, int classNumber,
                           boolean active, long rowVersion) {
    public StudentClass {
        requireText(classId, "classId");
        requireText(majorId, "majorId");
        requireText(classCode, "classCode");
        requireText(className, "className");
        if (enrollmentYear < 2000 || enrollmentYear > 2099) {
            throw new IllegalArgumentException("enrollmentYear must be between 2000 and 2099");
        }
        if (classNumber < 1 || classNumber > 9) {
            throw new IllegalArgumentException("classNumber must be between 1 and 9");
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
