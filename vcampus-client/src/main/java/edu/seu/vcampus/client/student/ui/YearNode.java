package edu.seu.vcampus.client.student.ui;

import java.time.Year;

/** Virtual year-level node in the organization tree. Not persisted. */
public record YearNode(int grade, int enrollmentYear, String majorId) {
    public static YearNode of(int grade, String majorId) {
        return new YearNode(grade, Year.now().getValue() - grade + 1, majorId);
    }

    public String displayName() {
        String gradeName = switch (grade) {
            case 1 -> "大一"; case 2 -> "大二";
            case 3 -> "大三"; case 4 -> "大四"; default -> grade + "级";
        };
        return gradeName + " (" + enrollmentYear + ")";
    }

    @Override public String toString() { return displayName(); }
}
