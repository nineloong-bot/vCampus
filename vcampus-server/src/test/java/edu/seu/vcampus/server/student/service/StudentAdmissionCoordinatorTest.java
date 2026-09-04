package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.CreateStudentManualCommand;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.routing.RequestContext;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.numbering.AccessCampusCardNumberGenerator;
import edu.seu.vcampus.server.student.numbering.AccessStudentNumberGenerator;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import edu.seu.vcampus.server.user.service.ProvisionedUserAccount;
import edu.seu.vcampus.server.user.service.UserAccountProvisioningPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAdmissionCoordinatorTest {
    private StudentAccessTestDatabase database;
    private StudentAdmissionCoordinator coordinator;

    @BeforeEach
    void setUp() throws Exception {
        database = new StudentAccessTestDatabase();
        var organizations = new AccessOrganizationRepository();
        database.transactions().inTransaction(connection -> {
            organizations.insertDepartment(connection,
                    new Department("department-1", "CS", "计算机学院", true, 0));
            organizations.insertMajor(connection,
                    new Major("major-1", "department-1", "090", "计算机科学", true, 0));
            organizations.insertClass(connection,
                    new StudentClass("class-1", "major-1", "090-24-1", "计科24-1", 2024, 1, true, 0));
            return null;
        });
        var sequences = new NumberSequenceRepository();
        UserAccountProvisioningPort accounts = (transaction, loginId, password) -> {
            String userId = UUID.randomUUID().toString();
            try (var statement = transaction.connection().prepareStatement(
                    "INSERT INTO tblUser (userId, loginId) VALUES (?, ?)")) {
                statement.setString(1, userId);
                statement.setString(2, loginId);
                statement.executeUpdate();
            } catch (java.sql.SQLException error) {
                throw new IllegalStateException(error);
            }
            return new ProvisionedUserAccount(userId, loginId,
                    edu.seu.vcampus.common.user.UserRole.STUDENT,
                    edu.seu.vcampus.common.user.AccountStatus.ACTIVE);
        };
        coordinator = new StudentAdmissionCoordinator(database.transactions(),
                new StripedResourceLockManager(), new RequestDeduplicator(database.transactions()),
                organizations, new AccessCampusCardNumberGenerator(sequences),
                new AccessStudentNumberGenerator(sequences), accounts,
                new StudentRepository(), new StudentChangeRepository());
    }

    @Test
    void admissionCreatesAccountProfileAuditAndBothNumbers() throws Exception {
        var result = coordinator.admit(command(), request("8e7c1a21-9d44-4c82-978b-df34326a0341"));

        assertThat(result.campusCardNumber()).isEqualTo("213240001");
        assertThat(result.studentNumber()).isEqualTo("09024101");
        assertThat(result.mustChangePassword()).isTrue();
        assertThat(database.count("tblUser")).isEqualTo(1);
        assertThat(database.count("tblStudent")).isEqualTo(1);
        assertThat(database.count("tblStudentChange")).isEqualTo(1);
    }

    @Test
    void replayReturnsOriginalResultWithoutAllocatingAgain() throws Exception {
        String requestId = "8e7c1a21-9d44-4c82-978b-df34326a0341";
        var first = coordinator.admit(command(), request(requestId));
        assertThat(database.stringValue("SELECT processingStatus FROM tblRequestDedup"))
                .isEqualTo("COMPLETED");
        var replay = coordinator.admit(command(), request(requestId));

        assertThat(replay).isEqualTo(first);
        assertThat(database.sequenceValue("CAMPUS_CARD_GLOBAL")).isEqualTo(1);
        assertThat(database.sequenceValue("STUDENT_NUMBER:090:24:1")).isEqualTo(1);
    }

    @Test
    void manualCreationKeepsSuppliedIdentifiersAndLeavesContactEmpty() throws Exception {
        var result = coordinator.createManual(manualCommand(),
                request("62d720ff-e845-4ab4-8e52-a561541fbe81"));

        assertThat(result.campusCardNumber()).isEqualTo("213240099");
        assertThat(result.studentNumber()).isEqualTo("09024199");
        assertThat(result.mustChangePassword()).isTrue();
        assertThat(result.student().email()).isNull();
        assertThat(result.student().phone()).isNull();
        assertThat(database.stringValue("SELECT idDocumentNumber FROM tblStudent WHERE studentNumber='09024199'"))
                .isEqualTo("110105200009030011");
        assertThat(database.stringValue("SELECT birthDate FROM tblStudent WHERE studentNumber='09024199'"))
                .startsWith("2000-09-03");
        assertThat(database.sequenceValue("CAMPUS_CARD_GLOBAL")).isZero();
    }

    @ParameterizedTest
    @EnumSource(AdmissionFailurePoint.class)
    void everyInjectedFailureRollsBackEverything(AdmissionFailurePoint point) throws Exception {
        coordinator.setFailureInjector(reached -> {
            if (reached == point) throw new InjectedAdmissionFailure(point);
        });

        assertThatThrownBy(() -> coordinator.admit(command(), request(UUID.randomUUID().toString())))
                .isInstanceOf(InjectedAdmissionFailure.class);
        assertThat(database.sequenceValue("CAMPUS_CARD_GLOBAL")).isZero();
        assertThat(database.sequenceValue("STUDENT_NUMBER:090:24:1")).isZero();
        assertThat(database.count("tblUser")).isZero();
        assertThat(database.count("tblStudent")).isZero();
        assertThat(database.count("tblStudentChange")).isZero();
        assertThat(database.count("tblRequestDedup")).isZero();
    }

    private static CreateStudentAdmissionCommand command() {
        return new CreateStudentAdmissionCommand("张三", "MALE", "zhangsan@seu.edu.cn",
                "13800000000", "major-1", "class-1", 2024, StudentType.UNDERGRADUATE);
    }

    private static CreateStudentManualCommand manualCommand() {
        return new CreateStudentManualCommand("213240099", "09024199", "李雷", "男",
                StudentType.UNDERGRADUATE, "居民身份证", "110105200009030011",
                LocalDate.of(2000, 9, 3), LocalDate.of(2024, 9, 1), "class-1");
    }

    private static RequestContext request(String requestId) {
        return new RequestContext(requestId, "admin-1", "test-client");
    }
}
