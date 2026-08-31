package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.ForbiddenException;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.service.PasswordHasher;
import edu.seu.vcampus.server.user.service.UserService;
import edu.seu.vcampus.server.user.service.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserHandlerRejectionAuditTest {
    private static final ClientContext CONTEXT =
            new ClientContext("connection", "10.0.0.7");
    private TransactionManager transactions;
    private UserService users;

    @BeforeEach
    void createIsolatedUserDatabase() throws Exception {
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
        users = new UserServiceImpl(transactions, new StripedResourceLockManager(),
                new AccessUserRepository(), new AccessAuditRepository(), new PasswordHasher());
    }

    @Test
    void malformedBodyIsAuditedOnceWithoutRequestContent() {
        String sensitiveBody = "password=Secret123 token=full-token";
        ResponseBody<?> response = route(new RejectingAuthorization(),
                "USER_UPDATE_ROLE", sensitiveBody);

        assertThat(response.code()).isEqualTo("COMMON_VALIDATION_FAILED");
        AuditRow audit = onlyAudit("USER_UPDATE_ROLE");
        assertThat(audit).isEqualTo(new AuditRow(null, null,
                "COMMON_VALIDATION_FAILED", "10.0.0.7"));
        assertThat(audit.toString()).doesNotContain("Secret123", "full-token", sensitiveBody);
    }

    @Test
    void expiredPasswordChangeSessionIsAuditedBeforeService() {
        ResponseBody<?> response = route(new ExpiredAuthorization(),
                "USER_CHANGE_PASSWORD", new ChangePasswordCommand(
                        "OldPass123".toCharArray(), "NewPass123".toCharArray()));

        assertThat(response.code()).isEqualTo("AUTH_SESSION_EXPIRED");
        assertThat(onlyAudit("USER_CHANGE_PASSWORD")).isEqualTo(
                new AuditRow(null, null, "AUTH_SESSION_EXPIRED", "10.0.0.7"));
    }

    @Test
    void retiredRoleUpdateAuditsTargetWithoutRunningAuthorization() {
        ResponseBody<?> response = route(new RejectingAuthorization(),
                "USER_UPDATE_ROLE", new UpdateUserRoleCommand(
                        "target-user", UserRole.TEACHER, 0));

        assertThat(response.code()).isEqualTo("COMMON_VALIDATION_FAILED");
        assertThat(onlyAudit("USER_UPDATE_ROLE")).isEqualTo(
                new AuditRow(null, "target-user", "COMMON_VALIDATION_FAILED", "10.0.0.7"));
    }

    @Test
    void serviceFailureIsNotAuditedAgainByHandler() {
        ResponseBody<?> response = route(new RejectingAuthorization(), "USER_REGISTER",
                new edu.seu.vcampus.common.user.TeacherAccountApplicationCommand(
                        "TEACHER01", "weakpass".toCharArray()));

        assertThat(response.code()).isEqualTo("AUTH_PASSWORD_POLICY_VIOLATION");
        assertThat(countAudits("USER_REGISTER")).isEqualTo(1);
    }

    private ResponseBody<?> route(AuthorizationPort authorization, String command,
                                  Serializable body) {
        MessageRouter router = new MessageRouter(Map.of());
        new UserHandlers(router, users, authorization);
        return router.route(new Message(UUID.randomUUID().toString(), MessageType.REQUEST,
                command, "full-token", body, 0), CONTEXT);
    }

    private AuditRow onlyAudit(String actionCode) {
        return transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT userId,targetId,resultCode,clientAddress FROM tblAuditLog "
                            + "WHERE actionCode=?")) {
                statement.setString(1, actionCode);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    AuditRow row = new AuditRow(rows.getString(1), rows.getString(2),
                            rows.getString(3), rows.getString(4));
                    assertThat(rows.next()).isFalse();
                    return row;
                }
            }
        });
    }

    private long countAudits(String actionCode) {
        return transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM tblAuditLog WHERE actionCode=?")) {
                statement.setString(1, actionCode);
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getLong(1);
                }
            }
        });
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule
                : Path.of("vcampus-database", folder, name);
    }

    private static void execute(java.sql.Connection connection, Path path) throws Exception {
        for (String sql : Files.readString(path).split(";")) {
            if (!sql.isBlank()) try (var statement = connection.createStatement()) {
                statement.execute(sql.strip());
            }
        }
    }

    private record AuditRow(String actor, String target, String result, String address) { }

    private static final class RejectingAuthorization implements AuthorizationPort {
        @Override public UserIdentity requireSession(String token) {
            throw new AssertionError("authorization must not run for this request");
        }
        @Override public void requirePermission(String token, String permission) {
            throw new AssertionError("authorization must not run for this request");
        }
    }

    private static final class ExpiredAuthorization implements AuthorizationPort {
        @Override public UserIdentity requireSession(String token) {
            throw new SessionExpiredException();
        }
        @Override public void requirePermission(String token, String permission) {
            throw new AssertionError("permission check not expected");
        }
    }

    private static final class ForbiddenAuthorization implements AuthorizationPort {
        @Override public UserIdentity requireSession(String token) {
            throw new AssertionError("permission denial must short-circuit identity lookup");
        }
        @Override public void requirePermission(String token, String permission) {
            throw new ForbiddenException();
        }
    }
}
