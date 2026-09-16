package edu.seu.vcampus.server.student.majortransfer.security;

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
    private static final String CS_ADMIN = "user-cse-admin";
    private static final String MATH_ADMIN = "user-math-admin";
    private static final String APPLICATION_TO_CS = "transfer-app-01";
    private static final String DRAFT_APPLICATION = "draft-transfer-app";

    private MajorTransferCollegeAuthorizationService authorization;
    private TransactionManager transactions;

    @BeforeEach
    void setUp() throws Exception {
        Path database = Path.of("target", "test-data", UUID.randomUUID() + ".accdb");
        Files.createDirectories(database.getParent());
        Files.copy(distributionDatabase(), database);
        ConnectionProvider provider = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
        transactions = new TransactionManager(provider);
        insertApplication(DRAFT_APPLICATION,
                "student-2024-030", "DRAFT", "70124106", "冯明轩");
        authorization = new MajorTransferCollegeAuthorizationService(transactions);
    }

    @Test
    void sourceAdministratorCanApproveOnlyTheSourceStage() {
        assertThatCode(() -> authorization.requireSourceApproval(MATH_ADMIN, APPLICATION_TO_CS))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireTargetApproval(MATH_ADMIN, APPLICATION_TO_CS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test
    void targetAdministratorCanApproveOnlyTheTargetStage() {
        assertThatCode(() -> authorization.requireTargetApproval(CS_ADMIN, APPLICATION_TO_CS))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireSourceApproval(CS_ADMIN, APPLICATION_TO_CS))
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
            statement.setString(1, "dept-cse");
                try (var result = statement.executeQuery()) {
                    result.next();
                    return result.getString(1);
                }
            }
        });

        assertThatCode(() -> authorization.requireTargetApprovalForOption(
                CS_ADMIN, mathOption)).doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireTargetApprovalForOption(
                MATH_ADMIN, mathOption)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test
    void unrelatedAdministratorCannotReadApplication() {
        assertThatThrownBy(() -> authorization.requireCanRead("user-super-admin", APPLICATION_TO_CS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    @Test
    void evenRelatedAdministratorsCannotReadStudentDraft() {
        assertThatThrownBy(() -> authorization.requireCanRead(MATH_ADMIN, DRAFT_APPLICATION))
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
                statement.setString(1, APPLICATION_TO_CS);
                statement.setBytes(2, new byte[] { 1 });
                statement.executeUpdate();
            }
            return null;
        });

        assertThatCode(() -> authorization.requireCanReadAttachment(CS_ADMIN, "attachment-ee"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireCanReadAttachment("user-super-admin", "attachment-ee"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
        assertThatThrownBy(() -> authorization.requireCanReadAttachment("user-super-admin", "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMMON_FORBIDDEN");
    }

    private static Path distributionDatabase() {
        Path current = Path.of("").toAbsolutePath();
        Path root = current.getFileName().toString().equals("vcampus-server")
                ? current.resolve("..") : current;
        return root.resolve("vcampus-distribution/data/vCampus.accdb").normalize();
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
                statement.setString(index++, "transfer-2026-autumn");
                statement.setString(index++, studentId);
                statement.setString(index++, "ORDINARY");
                statement.setString(index++, status);
                statement.setString(index++, "option-cs");
                statement.setString(index++, "dept-math");
                statement.setString(index++, "数学学院");
                statement.setString(index++, "major-math");
                statement.setString(index++, "数学与应用数学");
                statement.setString(index++, "class-math-2024");
                statement.setString(index++, "数学与应用数学2401班");
                statement.setString(index++, studentNumber);
                statement.setString(index++, "3");
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
