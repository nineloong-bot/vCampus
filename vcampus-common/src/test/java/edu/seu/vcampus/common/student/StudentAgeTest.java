package edu.seu.vcampus.common.student;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAgeTest {
    @Test
    void calculatesStandardAge() {
        LocalDate birth = LocalDate.of(2006, 3, 15);
        LocalDate asOfBeforeBirthday = LocalDate.of(2024, 3, 14);
        LocalDate asOfOnBirthday = LocalDate.of(2024, 3, 15);

        assertThat(StudentAge.calculateAge(birth, asOfBeforeBirthday)).isEqualTo(17);
        assertThat(StudentAge.calculateAge(birth, asOfOnBirthday)).isEqualTo(18);
    }

    @Test
    void normalizesTruncatedCentury() {
        LocalDate truncatedBirth = LocalDate.of(7, 2, 6);
        LocalDate asOf = LocalDate.of(2026, 9, 1);

        assertThat(StudentAge.calculateAge(truncatedBirth, asOf)).isEqualTo(19);
    }

    @Test
    void handlesNullAndFutureDatesGracefully() {
        assertThat(StudentAge.calculateAge(null, LocalDate.now())).isEqualTo(0);
        assertThat(StudentAge.calculateAge(LocalDate.now(), null)).isEqualTo(0);
        assertThat(StudentAge.calculateAge(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 1, 1))).isEqualTo(0);
    }
}
