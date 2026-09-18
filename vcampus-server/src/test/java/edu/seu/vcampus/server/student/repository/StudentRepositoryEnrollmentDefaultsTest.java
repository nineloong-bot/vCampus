package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StudentRepositoryEnrollmentDefaultsTest {
    private StudentAccessTestDatabase database;
    private StudentRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        database = new StudentAccessTestDatabase();
        repository = new StudentRepository();
    }

    @Test
    void standardAdmissionInsertDefaultsToEnrolledAndOnCampus() throws Exception {
        database.transactions().inTransaction(connection -> {
            repository.insert(connection, student("student-standard"));
            return null;
        });

        assertEnrollmentDefaults("student-standard");
    }

    @Test
    void manualAndFreshmanInsertDefaultsToEnrolledAndOnCampus() throws Exception {
        database.transactions().inTransaction(connection -> {
            repository.insertManual(connection, student("student-manual"),
                    "居民身份证", "110105200001010010", LocalDate.of(2000, 1, 1));
            return null;
        });

        assertEnrollmentDefaults("student-manual");
    }

    private void assertEnrollmentDefaults(String studentId) throws Exception {
        assertThat(database.stringValue(
                "SELECT enrolled FROM tblStudent WHERE studentId='" + studentId + "'"))
                .isEqualTo("TRUE");
        assertThat(database.stringValue(
                "SELECT onCampus FROM tblStudent WHERE studentId='" + studentId + "'"))
                .isEqualTo("TRUE");
    }

    private static Student student(String studentId) {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        return new Student(studentId, "user-1", "09026101", StudentType.UNDERGRADUATE,
                "测试学生", "男", null, null, "major-1", "class-1",
                LocalDate.of(2026, 9, 1), StudentStatus.ACTIVE, 0, now, now);
    }
}
