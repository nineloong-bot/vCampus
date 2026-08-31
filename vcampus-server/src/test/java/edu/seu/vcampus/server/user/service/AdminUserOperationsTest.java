package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.ConcurrentModificationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminUserOperationsTest {
    private static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";
    private static final ClientContext ADMIN_CONTEXT =
            new ClientContext("admin-connection", "10.0.0.10");
    private TransactionManager transactions;
    private UserRepository repository;
    private UserService service;
    private ConnectionProvider connections;
    private SessionRegistry sessions;

    @BeforeEach
    void createService() throws Exception {
        Path database = Path.of("target", "test-data", UUID.randomUUID() + ".accdb");
        Files.createDirectories(database.getParent());
        connections = () -> DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
        transactions = new TransactionManager(connections);
        try (var connection = connections.open()) {
            execute(connection, projectFile("schema", "010_user.sql"));
            execute(connection, projectFile("seed", "010_roles_permissions.sql"));
        }
        repository = new AccessUserRepository();
        sessions = new SessionRegistry();
        service = new UserServiceImpl(transactions, new StripedResourceLockManager(), repository,
                new AccessAuditRepository(), new PasswordHasher(), sessions,
                java.time.Clock.systemUTC());
    }

    @Test
    void searchReturnsSafePagedSummaries() {
        insert(account("ALICE", UserRole.TEACHER));
        insert(account("ALINA", UserRole.TEACHER));

        PageResult<?> result = service.searchUsers(new UserSearchQuery("ali", UserRole.TEACHER,
                AccountStatus.ACTIVE, 0, 1));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().toString()).doesNotContain("hash", "salt");
    }

    @Test
    void preventsDemotingTheOnlyActiveAdministrator() {
        assertThatThrownBy(() -> service.updateRole(ADMIN_ID, new UpdateUserRoleCommand(
                ADMIN_ID, UserRole.TEACHER, 0), ADMIN_CONTEXT))
                .hasMessage("USER_LAST_ADMIN_PROTECTED");

        assertAudit("USER_UPDATE_ROLE", "USER_LAST_ADMIN_PROTECTED", ADMIN_ID, ADMIN_ID);
    }

    @Test
    void disablingAnAccountRevokesItsExistingSessionAndAuditsTheChange() throws Exception {
        UserAccount target = account("TARGET", UserRole.TEACHER);
        insert(target);
        String token = service.login(new LoginCommand("TARGET", "Pass1234".toCharArray(), "client"),
                new ClientContext("connection", "127.0.0.1")).sessionToken();

        var view = service.changeStatus(ADMIN_ID, new ChangeUserStatusCommand(target.userId(),
                AccountStatus.DISABLED, "reviewed", 1), ADMIN_CONTEXT);

        assertThat(view.rowVersion()).isEqualTo(2);
        assertThatThrownBy(() -> service.getCurrentUser(token))
                .isInstanceOf(SessionExpiredException.class);
        try (var connection = connections.open(); var statement = connection.prepareStatement(
                "SELECT resultCode FROM tblAuditLog WHERE actionCode='USER_CHANGE_STATUS'")) {
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("SUCCESS");
            }
        }
        assertAudit("USER_CHANGE_STATUS", "SUCCESS", ADMIN_ID, target.userId());
    }

    @Test
    void demotingAdministratorRevokesTheTargetsExistingSession() {
        UserAccount target = account("SECOND_ADMIN", UserRole.ADMIN);
        insert(target);
        String token = service.login(new LoginCommand(target.loginId(), "Pass1234".toCharArray(),
                "client"), new ClientContext("connection", "127.0.0.1")).sessionToken();

        service.updateRole(ADMIN_ID,
                new UpdateUserRoleCommand(target.userId(), UserRole.TEACHER, 1), ADMIN_CONTEXT);

        assertThatThrownBy(() -> sessions.requireSession(token))
                .isInstanceOf(SessionExpiredException.class);
        assertThat(service.login(new LoginCommand(target.loginId(), "Pass1234".toCharArray(),
                "client"), new ClientContext("connection", "127.0.0.1")).permissions())
                .isEmpty();
    }

    @Test
    void activeQueryPortExcludesNonActiveAccounts() {
        PasswordHash password = new PasswordHasher().hash("Pass1234".toCharArray());
        UserAccount pending = new UserAccount(UUID.randomUUID().toString(), "PENDING_USER",
                password.hash(), password.salt(), password.iterations(),
                UserRole.TEACHER, AccountStatus.PENDING, false, 0, null, null, 0,
                LocalDateTime.now(), LocalDateTime.now());
        insert(pending);
        UserQueryPort queries = (UserQueryPort) service;

        assertThat(queries.findByUserId(pending.userId())).isPresent();
        assertThat(queries.findActiveUser(pending.userId())).isEmpty();
    }

    @Test
    void rejectedStatusTransitionAuditsActorTargetAndStableCode() {
        UserAccount target = account("STATUS_TARGET", UserRole.TEACHER);
        insert(target);

        assertThatThrownBy(() -> service.changeStatus(ADMIN_ID,
                new ChangeUserStatusCommand(target.userId(), AccountStatus.PENDING,
                        "invalid transition", 0), ADMIN_CONTEXT))
                .hasMessage("USER_STATUS_CONFLICT");

        assertAudit("USER_CHANGE_STATUS", "USER_STATUS_CONFLICT",
                ADMIN_ID, target.userId());
    }

    private void assertAudit(String action, String resultCode, String actor, String target) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT userId, targetId, clientAddress FROM tblAuditLog "
                            + "WHERE actionCode=? AND resultCode=?")) {
                statement.setString(1, action);
                statement.setString(2, resultCode);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo(actor);
                    assertThat(result.getString(2)).isEqualTo(target);
                    assertThat(result.getString(3)).isEqualTo(ADMIN_CONTEXT.clientAddress());
                }
            }
            return null;
        });
    }

    @Test
    void roleAndStatusAuditRowsAttributeTheActingAdministratorAndRefreshUpdatedAt() throws Exception {
        String actorId = "00000000-0000-0000-0000-000000000001";
        UserAccount roleTarget = account("ROLE_TARGET", UserRole.TEACHER);
        UserAccount statusTarget = account("STATUS_TARGET", UserRole.TEACHER);
        insert(roleTarget);
        insert(statusTarget);
        Instant changedAt = Instant.parse("2026-09-01T03:04:05Z");
        service = new UserServiceImpl(transactions, new StripedResourceLockManager(), repository,
                new AccessAuditRepository(), new PasswordHasher(),
                new edu.seu.vcampus.server.session.SessionRegistry(
                        Clock.fixed(changedAt, ZoneOffset.UTC)),
                Clock.fixed(changedAt, ZoneOffset.UTC));

        var roleView = service.updateRole(actorId, new UpdateUserRoleCommand(
                roleTarget.userId(), UserRole.ADMIN, 0));
        var statusView = service.changeStatus(actorId, new ChangeUserStatusCommand(
                statusTarget.userId(), AccountStatus.DISABLED, "reviewed", 0));

        assertThat(roleView.updatedAt()).isEqualTo(LocalDateTime.ofInstant(changedAt, ZoneOffset.UTC));
        assertThat(statusView.updatedAt()).isEqualTo(LocalDateTime.ofInstant(changedAt, ZoneOffset.UTC));
        try (var connection = connections.open(); var statement = connection.prepareStatement("""
                SELECT userId, actionCode, targetType, targetId, resultCode
                FROM tblAuditLog
                WHERE actionCode IN ('USER_UPDATE_ROLE', 'USER_CHANGE_STATUS')
                ORDER BY actionCode
                """)) {
            try (var result = statement.executeQuery()) {
                assertAudit(result, actorId, "USER_CHANGE_STATUS", statusTarget.userId());
                assertAudit(result, actorId, "USER_UPDATE_ROLE", roleTarget.userId());
                assertThat(result.next()).isFalse();
            }
        }
    }

    @Test
    void staleRoleUpdateDoesNotRevokeTheTargetsExistingSession() {
        UserAccount target = account("CONCURRENT_TARGET", UserRole.ADMIN);
        insert(target);
        String token = service.login(new LoginCommand("CONCURRENT_TARGET",
                        "Pass1234".toCharArray(), "client"),
                new ClientContext("connection", "127.0.0.1")).sessionToken();

        assertThatThrownBy(() -> service.updateRole(
                "00000000-0000-0000-0000-000000000001",
                new UpdateUserRoleCommand(target.userId(), UserRole.TEACHER, 0)))
                .isInstanceOf(ConcurrentModificationException.class);

        assertThat(service.getCurrentUser(token).userId()).isEqualTo(target.userId());
    }

    @Test
    void unchangedRoleDoesNotRevokeTheTargetsExistingSession() {
        UserAccount target = account("UNCHANGED_TARGET", UserRole.ADMIN);
        insert(target);
        String token = service.login(new LoginCommand("UNCHANGED_TARGET",
                        "Pass1234".toCharArray(), "client"),
                new ClientContext("connection", "127.0.0.1")).sessionToken();

        service.updateRole("00000000-0000-0000-0000-000000000001",
                new UpdateUserRoleCommand(target.userId(), UserRole.ADMIN, 1));

        assertThat(service.getCurrentUser(token).userId()).isEqualTo(target.userId());
    }

    private static void assertAudit(java.sql.ResultSet result, String actorId,
                                    String action, String targetId) throws Exception {
        assertThat(result.next()).isTrue();
        assertThat(result.getString("userId")).isEqualTo(actorId).isNotEqualTo(targetId);
        assertThat(result.getString("actionCode")).isEqualTo(action);
        assertThat(result.getString("targetType")).isEqualTo("USER");
        assertThat(result.getString("targetId")).isEqualTo(targetId);
        assertThat(result.getString("resultCode")).isEqualTo("SUCCESS");
    }

    private void insert(UserAccount account) { transactions.inTransaction(connection -> { repository.insert(connection, account); return null; }); }
    private static UserAccount account(String loginId, UserRole role) {
        PasswordHash password = new PasswordHasher().hash("Pass1234".toCharArray());
        LocalDateTime now = LocalDateTime.of(2026, 8, 28, 10, 0);
        return new UserAccount(UUID.randomUUID().toString(), loginId, password.hash(), password.salt(),
                password.iterations(), role, AccountStatus.ACTIVE, false, 0, null, null, 0, now, now);
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule : Path.of("vcampus-database", folder, name);
    }

    private static void execute(java.sql.Connection connection, Path script) throws Exception {
        for (String sql : Files.readString(script).split(";")) {
            if (!sql.isBlank()) try (var statement = connection.createStatement()) { statement.execute(sql.strip()); }
        }
    }
}
