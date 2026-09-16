package edu.seu.vcampus.server.student.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CampusCardEnrollmentYearTest {
    @Test
    void readsEnrollmentYearFromFourthAndFifthCampusCardDigits() {
        assertThat(CampusCardEnrollmentYear.from("213240001")).isEqualTo(2024);
        assertThat(CampusCardEnrollmentYear.from("213260004")).isEqualTo(2026);
    }

    @Test
    void rejectsAnInvalidCampusCardInsteadOfGuessingFromStudentNumber() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> CampusCardEnrollmentYear.from("09023101"))
                .withMessage("STUDENT_CAMPUS_CARD_INVALID");
    }
}
