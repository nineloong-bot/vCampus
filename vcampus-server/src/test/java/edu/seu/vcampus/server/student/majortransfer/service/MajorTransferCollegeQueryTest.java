package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationQuery;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationView;
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
    private static final String OPEN_BATCH = "transfer-2026-autumn";
    private static final String MATH = "dept-math";
    private static final String CS = "dept-cse";
    private MajorTransferService service;

    @BeforeEach
    void setUp() throws Exception {
        Path database = Path.of("target", "test-data", UUID.randomUUID() + ".accdb");
        Files.createDirectories(database.getParent());
        Files.copy(distributionDatabase(), database);
        ConnectionProvider provider = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
        TransactionManager transactions = new TransactionManager(provider);
        service = new MajorTransferServiceImpl(transactions,
                new StripedResourceLockManager(), new MajorTransferRepository(),
                new StudentRepository(), new StudentChangeRepository(),
                new AccessOrganizationRepository(), unusedUsers());
    }

    @Test
    void collegeListContainsOnlySourceOrTargetMatches() {
        var visible = service.listApplicationsForCollege(
                new MajorTransferApplicationQuery(OPEN_BATCH, null, null), CS);

        assertThat(visible).extracting(MajorTransferApplicationView::applicationId)
                .contains(
                        "transfer-app-01", "transfer-app-02");
        assertThat(visible).allSatisfy(application -> {
            assertThat(application.sourceApprovalAllowed()).isFalse();
            assertThat(application.targetApprovalAllowed()).isTrue();
        });
        var mathVisible = service.listApplicationsForCollege(
                new MajorTransferApplicationQuery(OPEN_BATCH, null, null), MATH);
        assertThat(mathVisible).hasSize(5).allSatisfy(application ->
                assertThat(application.sourceApprovalAllowed()).isTrue());
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
        var options = service.listOptionsForCollege(OPEN_BATCH, CS);

        assertThat(options).isNotEmpty().allSatisfy(option ->
                assertThat(option.targetDepartmentId()).isEqualTo(CS));
    }

    private static Path distributionDatabase() {
        Path current = Path.of("").toAbsolutePath();
        Path root = current.getFileName().toString().equals("vcampus-server")
                ? current.resolve("..") : current;
        return root.resolve("vcampus-distribution/data/vCampus.accdb").normalize();
    }

    private static UserQueryPort unusedUsers() {
        return new UserQueryPort() {
            @Override public Optional<UserIdentity> findActiveUser(String userId) { return Optional.empty(); }
            @Override public Optional<UserIdentity> findByUserId(String userId) { return Optional.empty(); }
            @Override public Optional<UserIdentity> findByLoginId(String loginId) { return Optional.empty(); }
            @Override public boolean hasRole(String userId, UserRole role) { return false; }
        };
    }

}
