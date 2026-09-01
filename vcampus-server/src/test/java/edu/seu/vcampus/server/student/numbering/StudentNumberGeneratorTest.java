package edu.seu.vcampus.server.student.numbering;

import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentNumberGeneratorTest {
    private StudentAccessTestDatabase database;
    private StudentNumberGenerator generator;

    @BeforeEach
    void setUp() throws Exception {
        database = new StudentAccessTestDatabase();
        generator = new AccessStudentNumberGenerator(new NumberSequenceRepository());
    }

    @ParameterizedTest
    @CsvSource({"090,2024,1,9,09024110", "09J,2024,1,9,09J24110"})
    void formatsDocumentedStudentNumberExamples(
            String majorCode, int year, int classNumber, int currentValue, String expected)
            throws Exception {
        database.setSequence("STUDENT_NUMBER:" + majorCode + ":24:" + classNumber,
                currentValue, 99);

        String actual = database.transactions().inTransaction(connection -> generator.next(
                new TransactionContext(connection), majorCode, year, classNumber));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void startsEachClassAtOneAndKeepsSequencesIndependent() {
        String classOne = database.transactions().inTransaction(connection -> generator.next(
                new TransactionContext(connection), "090", 2024, 1));
        String classTwo = database.transactions().inTransaction(connection -> generator.next(
                new TransactionContext(connection), "090", 2024, 2));

        assertThat(classOne).isEqualTo("09024101");
        assertThat(classTwo).isEqualTo("09024201");
    }

    @Test
    void rejectsInvalidMajorYearAndClassBeforeCreatingSequence() {
        assertCode("STUDENT_MAJOR_CODE_INVALID", () -> inTransaction("09-", 2024, 1));
        assertCode("STUDENT_ENROLLMENT_YEAR_INVALID", () -> inTransaction("090", 2100, 1));
        assertCode("STUDENT_CLASS_NUMBER_INVALID", () -> inTransaction("090", 2024, 0));
    }

    @Test
    void reportsClassSequenceExhaustion() throws Exception {
        database.setSequence("STUDENT_NUMBER:090:24:1", 99, 99);
        assertCode("STUDENT_CLASS_SEQUENCE_EXHAUSTED", () -> inTransaction("090", 2024, 1));
    }

    private String inTransaction(String majorCode, int year, int classNumber) {
        return database.transactions().inTransaction(connection -> generator.next(
                new TransactionContext(connection), majorCode, year, classNumber));
    }

    private static void assertCode(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(StudentNumberingException.class)
                .extracting(error -> ((StudentNumberingException) error).code())
                .isEqualTo(code);
    }
}
