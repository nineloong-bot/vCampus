package edu.seu.vcampus.common.student;

import java.time.LocalDate;
import java.time.Period;

/**
 * Utility for robust student age calculation.
 */
public final class StudentAge {
    private StudentAge() { }

    /**
     * Calculates the student's full completed years (周岁) as of the reference date.
     *
     * <p>Handles null inputs gracefully and automatically normalizes truncated two-digit
     * or century-offset years (year &lt; 100) to the 2000s.</p>
     *
     * @param birthDate the date of birth, may be null
     * @param asOf the reference date, may be null
     * @return non-negative full completed years, or 0 if dates are missing or invalid
     */
    public static int calculateAge(LocalDate birthDate, LocalDate asOf) {
        if (birthDate == null || asOf == null) {
            return 0;
        }
        LocalDate normalizedBirth = birthDate;
        if (normalizedBirth.getYear() < 100) {
            normalizedBirth = normalizedBirth.plusYears(2000);
        }
        if (asOf.isBefore(normalizedBirth)) {
            return 0;
        }
        return Period.between(normalizedBirth, asOf).getYears();
    }
}
