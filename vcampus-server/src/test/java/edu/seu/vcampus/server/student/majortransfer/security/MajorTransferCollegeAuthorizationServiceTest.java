package edu.seu.vcampus.server.student.majortransfer.security;

import edu.seu.vcampus.server.bootstrap.DatabaseInitializer;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MajorTransferCollegeAuthorizationServiceTest {
    private static final String CS_ADMIN = "00000000-0000-0000-0000-000000000202";
    private static final String MATH_ADMIN = "00000000-0000-0000-0000-000000000203";
    private static final String EE_ADMIN = "00000000-0000-0000-0000-000000000208";
    private static final String APPLICATION_TO_MATH = "00000000-0000-0000-0000-000000001023";

    private MajorTransferCollegeAuthorizationService authorization;
    private TransactionManager transactions;

    @BeforeEach
    void setUp() throws Exception {
        Path database = Path.of("target", "test-data", UUID.randomUUID() + ".accdb");
        Files.createDirectories(database.getParent());
        DatabaseInitializer.main(new String[] {
                directory("schema").toString(), directory("seed").toString(), database.toString()
        });
        ConnectionProvider provider = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
        transactions = new TransactionManager(provider);
        authorization = new MajorTransferCollegeAuthorizationService(transactions);
    }

    @Test
    void sourceAdministratorCanApproveOnlyTheSourceStage() {
        assertThatCode(() -> authorization.requireSourceApproval(CS_ADMIN, APPLICATION_TO_MATH))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireTargetApproval(CS_ADMIN, APPLICATION_TO_MATH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test
    void targetAdministratorCanApproveOnlyTheTargetStage() {
        assertThatCode(() -> authorization.requireTargetApproval(MATH_ADMIN, APPLICATION_TO_MATH))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireSourceApproval(MATH_ADMIN, APPLICATION_TO_MATH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test
    void unrelatedAdministratorCannotReadApplication() {
        assertThatThrownBy(() -> authorization.requireCanRead(EE_ADMIN, APPLICATION_TO_MATH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test
    void attachmentUsesItsPersistedApplicationScope() {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO tblMajorTransferAttachment
                    (attachmentId, applicationId, fileName, contentType, fileSize, content, createdAt)
                    VALUES ('attachment-math', ?, 'proof.txt', 'text/plain', 1, ?, NOW())
                    """)) {
                statement.setString(1, APPLICATION_TO_MATH);
                statement.setBytes(2, new byte[] { 1 });
                statement.executeUpdate();
            }
            return null;
        });

        assertThatCode(() -> authorization.requireCanReadAttachment(MATH_ADMIN, "attachment-math"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireCanReadAttachment(EE_ADMIN, "attachment-math"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
        assertThatThrownBy(() -> authorization.requireCanReadAttachment(EE_ADMIN, "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    private static Path directory(String child) {
        Path current = Path.of("").toAbsolutePath();
        Path databaseModule = current.getFileName().toString().equals("vcampus-server")
                ? current.resolve("../vcampus-database") : current.resolve("vcampus-database");
        return databaseModule.resolve(child).normalize();
    }
}
