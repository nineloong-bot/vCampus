package edu.seu.vcampus.server.user.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.SecurityAuditView;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessAuditRepositoryTest {
    private static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";
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
            repository.record(connection, ADMIN_ID, "USER_CHANGE_STATUS", "USER", "target",
                    "SUCCESS", "127.0.0.1");
            return null;
        });

        try (var connection = provider.open(); var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT userId, targetType, targetId, clientAddress FROM tblAuditLog")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(ADMIN_ID);
            assertThat(result.getString(2)).isEqualTo("USER");
            assertThat(result.getString(3)).isEqualTo("target");
            assertThat(result.getString(4)).isEqualTo("127.0.0.1");
        }
    }

    @Test
    void searchesActorOrTargetWithFiltersPagingAndStableDescendingOrder() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 30, 9, 0);
        insertAudit("00000000-0000-0000-0000-000000000010", ADMIN_ID,
                "USER_UPDATE_ROLE", "TARGET-A", "SUCCESS", createdAt);
        insertAudit("00000000-0000-0000-0000-000000000020", null,
                "USER_UPDATE_ROLE", ADMIN_ID, "SUCCESS", createdAt);
        insertAudit("00000000-0000-0000-0000-000000000030", ADMIN_ID,
                "USER_LOGIN", "TARGET-B", "AUTH_INVALID_CREDENTIALS",
                createdAt.minusDays(1));

        PageResult<SecurityAuditView> page = transactions.inTransaction(connection ->
                repository.search(connection, new SecurityAuditQuery(
                        ADMIN_ID, "USER_UPDATE_ROLE", "SUCCESS",
                        createdAt.minusMinutes(1), createdAt.plusMinutes(1), 0, 1)));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(SecurityAuditView::auditId)
                .containsExactly("00000000-0000-0000-0000-000000000020");
        assertThat(page.items().getFirst()).extracting(
                SecurityAuditView::actorUserId,
                SecurityAuditView::targetId,
                SecurityAuditView::actionCode,
                SecurityAuditView::resultCode)
                .containsExactly(null, ADMIN_ID, "USER_UPDATE_ROLE", "SUCCESS");
        assertThat(Arrays.stream(SecurityAuditView.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("clientAddress", "password", "passwordHash", "salt",
                        "sessionToken");
    }

    private void insertAudit(String auditId, String actorUserId, String actionCode,
                             String targetId, String resultCode, LocalDateTime createdAt)
            throws Exception {
        try (var connection = provider.open(); var statement = connection.prepareStatement("""
                INSERT INTO tblAuditLog
                    (auditId, userId, actionCode, targetType, targetId,
                     resultCode, clientAddress, createdAt)
                VALUES (?, ?, ?, 'USER', ?, ?, 'sensitive-address', ?)
                """)) {
            statement.setString(1, auditId);
            statement.setString(2, actorUserId);
            statement.setString(3, actionCode);
            statement.setString(4, targetId);
            statement.setString(5, resultCode);
            statement.setTimestamp(6, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
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
