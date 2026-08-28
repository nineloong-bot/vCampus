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
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminUserOperationsTest {
    private TransactionManager transactions;
    private UserRepository repository;
    private UserService service;
    private ConnectionProvider connections;

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
        service = new UserServiceImpl(transactions, new StripedResourceLockManager(), repository,
                new AccessAuditRepository(), new PasswordHasher());
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
        String administrator = "00000000-0000-0000-0000-000000000001";

        assertThatThrownBy(() -> service.updateRole(new UpdateUserRoleCommand(
                administrator, UserRole.TEACHER, 0)))
                .hasMessage("USER_LAST_ADMIN_PROTECTED");
    }

    @Test
    void disablingAnAccountRevokesItsExistingSessionAndAuditsTheChange() throws Exception {
        UserAccount target = account("TARGET", UserRole.TEACHER);
        insert(target);
        String token = service.login(new LoginCommand("TARGET", "Pass1234".toCharArray(), "client"),
                new ClientContext("connection", "127.0.0.1")).sessionToken();

        var view = service.changeStatus(new ChangeUserStatusCommand(target.userId(),
                AccountStatus.DISABLED, "reviewed", 1));

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
