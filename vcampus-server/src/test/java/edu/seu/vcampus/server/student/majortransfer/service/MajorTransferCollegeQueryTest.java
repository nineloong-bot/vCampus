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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MajorTransferCollegeQueryTest {
    private static final String OPEN_BATCH = "00000000-0000-0000-0000-000000001001";
    private static final String MATH = "00000000-0000-0000-0000-000000000111";
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
        service = new MajorTransferServiceImpl(new TransactionManager(provider),
                new StripedResourceLockManager(), new MajorTransferRepository(),
                new StudentRepository(), new StudentChangeRepository(),
                new AccessOrganizationRepository(), mock(UserQueryPort.class));
    }

    @Test
    void collegeListContainsOnlySourceOrTargetMatches() {
        var visible = service.listApplicationsForCollege(
                new MajorTransferApplicationQuery(OPEN_BATCH, null, null), MATH);

        assertThat(visible).extracting(MajorTransferApplicationView::applicationId)
                .containsExactlyInAnyOrder(
                        "00000000-0000-0000-0000-000000001023",
                        "00000000-0000-0000-0000-000000001025",
                        "00000000-0000-0000-0000-000000001030")
                .doesNotContain("00000000-0000-0000-0000-000000001022");
        assertThat(visible).allSatisfy(application -> {
            assertThat(application.sourceApprovalAllowed()).isFalse();
            assertThat(application.targetApprovalAllowed()).isTrue();
        });
    }

    private static Path directory(String child) {
        Path current = Path.of("").toAbsolutePath();
        Path databaseModule = current.getFileName().toString().equals("vcampus-server")
                ? current.resolve("../vcampus-database") : current.resolve("vcampus-database");
        return databaseModule.resolve(child).normalize();
    }
}
