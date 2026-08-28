package edu.seu.vcampus.server.user.repository;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessAuditRepositoryTest {
    private ConnectionProvider provider;
    private TransactionManager transactions;
    private AuditRepository repository;

    @BeforeEach
    void createAccessDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        provider = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(provider);
        try (var connection = provider.open()) {
            executeScript(connection, projectFile("schema", "010_user.sql"));
            executeScript(connection, projectFile("seed", "010_roles_permissions.sql"));
        }
        repository = new AccessAuditRepository();
    }

    @Test
    void recordsAnonymousSecurityEventWithoutSensitivePayload() throws Exception {
        transactions.inTransaction(connection -> {
            repository.record(connection, null, "USER_LOGIN", "AUTH_INVALID_CREDENTIALS");
            return null;
        });

        try (var connection = provider.open();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT * FROM tblAuditLog")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("userId")).isNull();
            assertThat(result.getString("actionCode")).isEqualTo("USER_LOGIN");
            assertThat(result.getString("targetType")).isEqualTo("USER");
            assertThat(result.getString("targetId")).isNull();
            assertThat(result.getString("resultCode")).isEqualTo("AUTH_INVALID_CREDENTIALS");
            assertThat(result.getString("clientAddress")).isNull();
            assertThat(result.getTimestamp("createdAt")).isNotNull();
            assertThat(result.next()).isFalse();
        }
    }

    @Test
    void rollsBackAuditWithCallerTransaction() throws Exception {
        assertThatThrownBy(() -> transactions.inTransaction(connection -> {
            repository.record(connection, null, "USER_LOGIN", "SUCCESS");
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        try (var connection = provider.open();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM tblAuditLog")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }

    @Test
    void recordsAnExplicitTargetWithoutPersistingRequestPayload() throws Exception {
        transactions.inTransaction(connection -> {
            repository.record(connection, null, "USER_CHANGE_STATUS", "USER", "target", "SUCCESS");
            return null;
        });

        try (var connection = provider.open(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT targetType, targetId FROM tblAuditLog")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("USER");
            assertThat(result.getString(2)).isEqualTo("target");
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
