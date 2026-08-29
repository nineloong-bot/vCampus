package edu.seu.vcampus.common.course;

/** Shared message-boundary checks that mirror course schema text widths. */
final class CourseValidation {
    private CourseValidation() { }

    static void text(String name, String value, int maximumLength) {
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must contain 1 to " + maximumLength + " characters");
        }
    }

    static void optionalText(String name, String value, int maximumLength) {
        if (value != null && value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must not exceed " + maximumLength + " characters");
        }
    }
}
