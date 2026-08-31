package edu.seu.vcampus.server.user.repository;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;

import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static edu.seu.vcampus.common.user.UserRole.STUDENT;
import static org.assertj.core.api.Assertions.assertThat;

class AccessPermissionRepositoryTest {
    private TransactionManager transactions;
    private PermissionRepository permissions;

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
        permissions = new AccessPermissionRepository();
    }

    @Test
    void loadsRolePermissionsFromRolePermissionTable() {
        Set<String> administratorPermissions = transactions.inTransaction(connection ->
                permissions.findByRole(connection, ADMIN));
        Set<String> studentPermissions = transactions.inTransaction(connection ->
                permissions.findByRole(connection, STUDENT));

        assertThat(administratorPermissions)
                .containsExactlyInAnyOrder("USER_READ_ALL", "USER_ROLE_WRITE",
                        "USER_STATUS_WRITE", "USER_AUDIT_READ",
                        "USER_PASSWORD_RESET");
        assertThat(studentPermissions).isEmpty();
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
