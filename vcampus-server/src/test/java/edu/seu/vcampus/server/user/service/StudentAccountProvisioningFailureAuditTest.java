package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAccountProvisioningFailureAuditTest {
    private static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";
    private TransactionManager transactions;
    private StudentAccountProvisioningFailureAudit failureAudit;

    @BeforeEach
    void createDatabase() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(provider);
        try (var connection = provider.open()) {
            execute(connection, projectFile("schema", "010_user.sql"));
            execute(connection, projectFile("seed", "010_roles_permissions.sql"));
        }
        failureAudit = new StudentAccountProvisioningFailureAudit(
                transactions, new AccessAuditRepository());
    }

    @Test
    void recordsFailureInItsOwnShortTransactionAfterCallerRollback() {
        failureAudit.recordAfterRollback(ADMIN_ID, null,
                "USER_LOGIN_ID_EXISTS", "10.0.0.30");

        transactions.inTransaction(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery(
                         "SELECT userId, targetType, targetId, resultCode, clientAddress "
                                 + "FROM tblAuditLog")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(ADMIN_ID);
                assertThat(result.getString(2)).isEqualTo("USER");
                assertThat(result.getString(3)).isNull();
                assertThat(result.getString(4)).isEqualTo("USER_LOGIN_ID_EXISTS");
                assertThat(result.getString(5)).isEqualTo("10.0.0.30");
            }
            return null;
        });
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule : Path.of("vcampus-database", folder, name);
    }

    private static void execute(java.sql.Connection connection, Path path) throws Exception {
        for (String sql : Files.readString(path).split(";")) {
            if (!sql.isBlank()) try (var statement = connection.createStatement()) {
                statement.execute(sql.strip());
            }
        }
    }
}
