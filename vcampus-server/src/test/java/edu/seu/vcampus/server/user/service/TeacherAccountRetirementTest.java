package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeacherAccountRetirementTest {
    private TransactionManager transactions;
    private UserRepository users;
    private UserService service;

    @BeforeEach
    void createServiceWithIsolatedAccessDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(provider);
        try (var connection = provider.open()) {
            executeScript(connection, projectFile("schema", "010_user.sql"));
            executeScript(connection, projectFile("seed", "010_roles_permissions.sql"));
        }
        users = new AccessUserRepository();
        service = new UserServiceImpl(transactions, new StripedResourceLockManager(), users,
                new AccessAuditRepository(), new PasswordHasher());
    }

    @Test
    void compatibilityApiRejectsAndClearsSecretsWithoutCreatingAnAccount() {
        char[] source = "Password1".toCharArray();
        TeacherAccountApplicationCommand command =
                new TeacherAccountApplicationCommand("teacher01", source);

        assertThatThrownBy(() -> service.applyForTeacherAccount(command))
                .hasMessage("COMMON_VALIDATION_FAILED");

        assertThat(source).containsOnly('\0');
        assertThat(command.password()).containsOnly('\0');
        Optional<edu.seu.vcampus.server.user.domain.UserAccount> account =
                transactions.inTransaction(connection ->
                        users.findByNormalizedLoginId(connection, "TEACHER01"));
        assertThat(account).isEmpty();
    }

    @Test
    void compatibilityApiWithContextRecordsOnlyARejectionAudit() {
        TeacherAccountApplicationCommand command = new TeacherAccountApplicationCommand(
                "teacher02", "Password1".toCharArray());

        assertThatThrownBy(() -> service.applyForTeacherAccount(command,
                new ClientContext("connection", "10.0.0.8")))
                .hasMessage("COMMON_VALIDATION_FAILED");

        long rejectionCount = transactions.inTransaction(
                TeacherAccountRetirementTest::countRejections);
        assertThat(rejectionCount).isEqualTo(1);
        Optional<edu.seu.vcampus.server.user.domain.UserAccount> account =
                transactions.inTransaction(connection ->
                        users.findByNormalizedLoginId(connection, "TEACHER02"));
        assertThat(account).isEmpty();
    }

    private static long countRejections(java.sql.Connection connection) {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tblAuditLog "
                        + "WHERE actionCode=? AND resultCode=?")) {
            statement.setString(1, "USER_REGISTER");
            statement.setString(2, "COMMON_VALIDATION_FAILED");
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (java.sql.SQLException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule
                : Path.of("vcampus-database", folder, name);
    }

    private static void executeScript(java.sql.Connection connection, Path path)
            throws Exception {
        for (String sql : Files.readString(path).split(";")) {
            if (!sql.isBlank()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(sql.strip());
                }
            }
        }
    }
}
