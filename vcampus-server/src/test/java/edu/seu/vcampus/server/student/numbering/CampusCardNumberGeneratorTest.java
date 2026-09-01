package edu.seu.vcampus.server.student.numbering;

import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampusCardNumberGeneratorTest {
    private StudentAccessTestDatabase database;
    private CampusCardNumberGenerator generator;

    @BeforeEach
    void setUp() throws Exception {
        database = new StudentAccessTestDatabase();
        generator = new AccessCampusCardNumberGenerator(new NumberSequenceRepository());
    }

    @ParameterizedTest
    @CsvSource({
            "UNDERGRADUATE,2024,2477,213242478",
            "MASTER,2024,0,223240001",
            "DOCTORATE,2025,1,233250002"
    })
    void formatsDocumentedCampusCardExamples(
            StudentType type, int year, int currentValue, String expected) throws Exception {
        database.setSequence("CAMPUS_CARD_GLOBAL", currentValue, 9999);

        String actual = database.transactions().inTransaction(connection ->
                generator.next(new edu.seu.vcampus.server.persistence.TransactionContext(connection),
                        type, year));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void rejectsUnsupportedYearWithoutConsumingSequence() throws Exception {
        assertThatThrownBy(() -> database.transactions().inTransaction(connection ->
                generator.next(new edu.seu.vcampus.server.persistence.TransactionContext(connection),
                        StudentType.UNDERGRADUATE, 1999)))
                .isInstanceOf(StudentNumberingException.class)
                .extracting(error -> ((StudentNumberingException) error).code())
                .isEqualTo("STUDENT_ENROLLMENT_YEAR_INVALID");
        assertThat(database.sequenceValue("CAMPUS_CARD_GLOBAL")).isZero();
    }

    @Test
    void reportsGlobalSequenceExhaustion() throws Exception {
        database.setSequence("CAMPUS_CARD_GLOBAL", 9999, 9999);

        assertThatThrownBy(() -> database.transactions().inTransaction(connection ->
                generator.next(new edu.seu.vcampus.server.persistence.TransactionContext(connection),
                        StudentType.MASTER, 2024)))
                .isInstanceOf(StudentNumberingException.class)
                .extracting(error -> ((StudentNumberingException) error).code())
                .isEqualTo("STUDENT_CAMPUS_CARD_SEQUENCE_EXHAUSTED");
    }
}
