package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationQuery;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationView;
import edu.seu.vcampus.server.bootstrap.DatabaseInitializer;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.user.service.UserQueryPort;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.common.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
class MajorTransferCollegeQueryTest {
    private static final String OPEN_BATCH = "00000000-0000-0000-0000-000000001001";
    private static final String MATH = "bulk-dept-02";
    private static final String EE = "bulk-dept-09";
    private static final String CS = "bulk-dept-01";
    private MajorTransferService service;

    @BeforeEach
    void setUp() throws Exception {
        Path database = Path.of("target", "test-data", UUID.randomUUID() + ".accdb");
        Files.createDirectories(database.getParent());
        DatabaseInitializer.main(new String[] {
                directory("schema").toString(), directory("seed").toString(), database.toString()
        });
        ConnectionProvider provider = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
        TransactionManager transactions = new TransactionManager(provider);
        insertApplication(transactions, "00000000-0000-0000-0000-000000001021",
                "00000000-0000-0000-0000-000000000104", "SUBMITTED", "09023101", "李明");
        insertApplication(transactions, "00000000-0000-0000-0000-000000001022",
                "00000000-0000-0000-0000-000000000210", "SUBMITTED", "09023102", "张伟");
        insertApplication(transactions, "00000000-0000-0000-0000-000000001031",
                "00000000-0000-0000-0000-000000000230", "DRAFT", "09023111", "朱琳");
        service = new MajorTransferServiceImpl(transactions,
                new StripedResourceLockManager(), new MajorTransferRepository(),
                new StudentRepository(), new StudentChangeRepository(),
                new AccessOrganizationRepository(), unusedUsers());
    }

    @Test
    void collegeListContainsOnlySourceOrTargetMatches() {
        var visible = service.listApplicationsForCollege(
                new MajorTransferApplicationQuery(OPEN_BATCH, null, null), EE);

        assertThat(visible).extracting(MajorTransferApplicationView::applicationId)
                .contains(
                        "00000000-0000-0000-0000-000000001021",
                        "00000000-0000-0000-0000-000000001022");
        assertThat(visible).allSatisfy(application -> {
            assertThat(application.sourceApprovalAllowed()).isFalse();
            assertThat(application.targetApprovalAllowed()).isTrue();
        });
        var mathVisible = service.listApplicationsForCollege(
                new MajorTransferApplicationQuery(OPEN_BATCH, null, null), MATH);
        assertThat(mathVisible).isEmpty();
    }

    @Test
    void administrativeListsNeverExposeStudentDrafts() {
        var allApplications = service.listApplications(
                new MajorTransferApplicationQuery(OPEN_BATCH, null, null));
        var collegeApplications = service.listApplicationsForCollege(
                new MajorTransferApplicationQuery(OPEN_BATCH, null, null), CS);

        assertThat(allApplications).isNotEmpty().noneMatch(application ->
                application.status() == edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus.DRAFT);
        assertThat(collegeApplications).isNotEmpty().noneMatch(application ->
                application.status() == edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus.DRAFT);
    }

    @Test
    void collegeOptionListContainsOnlyPersistedTargetCollege() {
        var options = service.listOptionsForCollege(OPEN_BATCH, MATH);

        assertThat(options).isNotEmpty().allSatisfy(option ->
                assertThat(option.targetDepartmentId()).isEqualTo(MATH));
    }

    private static Path directory(String child) {
        Path current = Path.of("").toAbsolutePath();
        Path databaseModule = current.getFileName().toString().equals("vcampus-server")
                ? current.resolve("../vcampus-database") : current.resolve("vcampus-database");
        return databaseModule.resolve(child).normalize();
    }

    private static UserQueryPort unusedUsers() {
        return new UserQueryPort() {
            @Override public Optional<UserIdentity> findActiveUser(String userId) { return Optional.empty(); }
            @Override public Optional<UserIdentity> findByUserId(String userId) { return Optional.empty(); }
            @Override public Optional<UserIdentity> findByLoginId(String loginId) { return Optional.empty(); }
            @Override public boolean hasRole(String userId, UserRole role) { return false; }
        };
    }

    private static void insertApplication(TransactionManager transactions, String id,
                                          String studentId, String status,
                                          String studentNumber, String studentName) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO tblMajorTransferApplication
                    (applicationId,batchId,studentId,applicationType,applicationStatus,optionId,
                     fromDepartmentId,fromDepartmentName,fromMajorId,fromMajorName,fromClassId,
                     fromClassName,fromStudentNumber,fromGrade,studentName,reason,baseStudentVersion,
                     applicationVersion,submittedAt,createdAt,updatedAt)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW(),NOW())
                    """)) {
                int index = 1;
                statement.setString(index++, id);
                statement.setString(index++, OPEN_BATCH);
                statement.setString(index++, studentId);
                statement.setString(index++, "ORDINARY");
                statement.setString(index++, status);
                statement.setString(index++, "00000000-0000-0000-0000-000000001013");
                statement.setString(index++, CS);
                statement.setString(index++, "计算机学院");
                statement.setString(index++, "bulk-major-02");
                statement.setString(index++, "计算机科学");
                statement.setString(index++, "00000000-0000-0000-0000-000000000103");
                statement.setString(index++, "计算机科学与技术2301班");
                statement.setString(index++, studentNumber);
                statement.setString(index++, "2023");
                statement.setString(index++, studentName);
                statement.setString(index++, "测试申请");
                statement.setLong(index++, 0);
                statement.setLong(index, 1);
                statement.executeUpdate();
            }
            return null;
        });
    }
}
