package edu.seu.vcampus.server.student.service;

/** Resolves a student's enrollment cohort from the campus-card login identifier. */
public final class CampusCardEnrollmentYear {
    private static final String CAMPUS_CARD_PATTERN = "2[123]3[0-9]{6}";

    private CampusCardEnrollmentYear() {
    }

    /** Returns the four-digit cohort encoded by positions four and five. */
    public static int from(String campusCardNumber) {
        if (campusCardNumber == null || !campusCardNumber.matches(CAMPUS_CARD_PATTERN)) {
            throw new IllegalArgumentException("STUDENT_CAMPUS_CARD_INVALID");
        }
        return 2000 + Integer.parseInt(campusCardNumber.substring(3, 5));
    }
}
