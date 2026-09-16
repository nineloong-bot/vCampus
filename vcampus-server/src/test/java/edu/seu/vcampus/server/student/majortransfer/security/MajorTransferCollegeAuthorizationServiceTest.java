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
        insertApplication(APPLICATION_TO_EE,
                "00000000-0000-0000-0000-000000000104", "SUBMITTED", "09023101", "李明");
        insertApplication(DRAFT_APPLICATION,
                "00000000-0000-0000-0000-000000000230", "DRAFT", "09023111", "朱琳");
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
                statement.setString(1, "bulk-dept-02");
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

    private void insertApplication(String id, String studentId, String status,
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
                statement.setString(index++, "00000000-0000-0000-0000-000000001001");
                statement.setString(index++, studentId);
                statement.setString(index++, "ORDINARY");
                statement.setString(index++, status);
                statement.setString(index++, "00000000-0000-0000-0000-000000001013");
                statement.setString(index++, "bulk-dept-01");
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
