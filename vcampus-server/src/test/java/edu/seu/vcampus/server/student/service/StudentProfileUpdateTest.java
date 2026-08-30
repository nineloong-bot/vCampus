package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.ChangeStudentStatusCommand;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.UpdateStudentContactCommand;
import edu.seu.vcampus.common.student.UpdateStudentEnrollmentCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.student.domain.Student;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ConcurrentModificationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentProfileUpdateTest {
    private StudentAccessTestDatabase database;
    private StudentService service;

    @BeforeEach
    void setUp() throws Exception {
        database = new StudentAccessTestDatabase();
        var repository = new StudentRepository();
        database.transactions().inTransaction(connection -> {
            StudentFixtures.insertOrganization(connection, new AccessOrganizationRepository());
            repository.insert(connection, student(StudentStatus.ACTIVE));
            return null;
        });
        service = new StudentServiceImpl(database.transactions(), new StripedResourceLockManager(),
                repository, new StudentChangeRepository(), new AccessOrganizationRepository(),
                StudentFixtures.userQueries("user-1", "213240001"), "admin-1");
    }

    @Test
    void contactUpdateIncrementsVersionAndDoesNotWriteEnrollmentAudit() throws Exception {
        var updated = service.updateContact(new UpdateStudentContactCommand(
                "student-1", "new@seu.edu.cn", "13900000000", 0));

        assertThat(updated.email()).isEqualTo("new@seu.edu.cn");
        assertThat(updated.phone()).isEqualTo("13900000000");
        assertThat(updated.rowVersion()).isEqualTo(1);
        assertThat(database.count("tblStudentChange")).isZero();
    }

    @Test
    void staleStatusUpdateDoesNotWriteChangeHistory() throws Exception {
        assertThatThrownBy(() -> service.changeStatus(new ChangeStudentStatusCommand(
                "student-1", StudentStatus.SUSPENDED, LocalDate.now(), "休学", 9)))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThat(database.count("tblStudentChange")).isZero();
    }

    @Test
    void statusAndAuditCommitTogether() throws Exception {
        var updated = service.changeStatus(new ChangeStudentStatusCommand(
                "student-1", StudentStatus.SUSPENDED, LocalDate.now(), "休学", 0));

        assertThat(updated.status()).isEqualTo(StudentStatus.SUSPENDED);
        assertThat(updated.rowVersion()).isEqualTo(1);
        assertThat(database.count("tblStudentChange")).isEqualTo(1);
    }

    @Test
    void enrollmentChangeAllocatesTargetClassNumberAndWritesAudit() throws Exception {
        database.transactions().inTransaction(connection -> {
            new AccessOrganizationRepository().insertClass(connection,
                    new StudentClass("class-2", "major-1", "090-24-2", "计科24-2", 2024, 2, true, 0));
            return null;
        });

        var updated = service.updateEnrollment(new UpdateStudentEnrollmentCommand(
                "student-1", "class-2", LocalDate.now(), "转班", 0));

        assertThat(updated.classId()).isEqualTo("class-2");
        assertThat(updated.studentNumber()).isEqualTo("09024201");
        assertThat(updated.campusCardNumber()).isEqualTo("213240001");
        assertThat(database.count("tblStudentChange")).isEqualTo(1);
    }

    @Test
    void terminalStatusCannotReturnToActive() throws Exception {
        database.transactions().inTransaction(connection -> {
            new StudentRepository().updateStatus(connection, "student-1", "GRADUATED", 0,
                    Instant.now());
            return null;
        });

        assertThatThrownBy(() -> service.changeStatus(new ChangeStudentStatusCommand(
                "student-1", StudentStatus.ACTIVE, LocalDate.now(), "错误恢复", 1)))
                .isInstanceOf(StudentAdmissionException.class)
                .extracting(error -> ((StudentAdmissionException) error).code())
                .isEqualTo("STUDENT_STATUS_TRANSITION_INVALID");
        assertThat(database.count("tblStudentChange")).isZero();
    }

    static Student student(StudentStatus status) {
        Instant now = Instant.parse("2024-09-01T00:00:00Z");
        return new Student("student-1", "user-1", "09024101", StudentType.UNDERGRADUATE,
                "张三", "MALE", "old@seu.edu.cn", "13800000000", "major-1", "class-1",
                LocalDate.of(2024, 9, 1), status, 0, now, now);
    }
}
