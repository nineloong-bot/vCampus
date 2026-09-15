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
    private static final String APPLICATION_TO_EE = "00000000-0000-0000-0000-000000001021";
    private static final String DRAFT_APPLICATION = "00000000-0000-0000-0000-000000001031";

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
        assertThatCode(() -> authorization.requireSourceApproval(CS_ADMIN, APPLICATION_TO_EE))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireTargetApproval(CS_ADMIN, APPLICATION_TO_EE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test
    void targetAdministratorCanApproveOnlyTheTargetStage() {
        assertThatCode(() -> authorization.requireTargetApproval(EE_ADMIN, APPLICATION_TO_EE))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireSourceApproval(EE_ADMIN, APPLICATION_TO_EE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test
    void targetAdministratorCanUseOnlyItsPersistedOption() {
        String mathOption = transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    SELECT optionId FROM tblMajorTransferOption
                    WHERE targetDepartmentId=?
                    """)) {
                statement.setString(1, "00000000-0000-0000-0000-000000000111");
                try (var result = statement.executeQuery()) {
                    result.next();
                    return result.getString(1);
                }
            }
        });

        assertThatCode(() -> authorization.requireTargetApprovalForOption(
                MATH_ADMIN, mathOption)).doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireTargetApprovalForOption(
                CS_ADMIN, mathOption)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test
    void unrelatedAdministratorCannotReadApplication() {
        assertThatThrownBy(() -> authorization.requireCanRead(MATH_ADMIN, APPLICATION_TO_EE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test
    void evenRelatedAdministratorsCannotReadStudentDraft() {
        assertThatThrownBy(() -> authorization.requireCanRead(CS_ADMIN, DRAFT_APPLICATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test
    void attachmentUsesItsPersistedApplicationScope() {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO tblMajorTransferAttachment
                    (attachmentId, applicationId, fileName, contentType, fileSize, content, createdAt)
                    VALUES ('attachment-ee', ?, 'proof.txt', 'text/plain', 1, ?, NOW())
                    """)) {
                statement.setString(1, APPLICATION_TO_EE);
                statement.setBytes(2, new byte[] { 1 });
                statement.executeUpdate();
            }
            return null;
        });

        assertThatCode(() -> authorization.requireCanReadAttachment(EE_ADMIN, "attachment-ee"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireCanReadAttachment(MATH_ADMIN, "attachment-ee"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
        assertThatThrownBy(() -> authorization.requireCanReadAttachment(MATH_ADMIN, "missing"))
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
