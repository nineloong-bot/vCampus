package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAuditFailureIsolationTest {
    private static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";
    private TransactionManager transactions;
    private UserRepository users;
    private PasswordHasher hasher;

    @BeforeEach
    void createDatabase() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(provider);
        try (Connection connection = provider.open()) {
            execute(connection, projectFile("schema", "010_user.sql"));
            execute(connection, projectFile("seed", "010_roles_permissions.sql"));
        }
        users = new AccessUserRepository();
        hasher = new PasswordHasher();
    }

    @Test
    void failureAuditOutageCannotReplaceStableRegistrationOrLoginErrors() {
        UserService healthy = service(new AccessAuditRepository());
        healthy.applyForTeacherAccount(new TeacherAccountApplicationCommand(
                "TEACHER01", "Password1".toCharArray()));
        insert(account("LOGIN_TARGET", UserRole.TEACHER));
        ThrowingAudits failing = new ThrowingAudits(false);
        UserService subject = service(failing);

        assertThatThrownBy(() -> subject.applyForTeacherAccount(
                new TeacherAccountApplicationCommand("TEACHER01", "Password1".toCharArray())))
                .hasMessage("USER_LOGIN_ID_EXISTS");
        assertThatThrownBy(() -> subject.login(new LoginCommand(
                "LOGIN_TARGET", "WrongPass1".toCharArray(), "client"),
                new ClientContext("connection", "127.0.0.1")))
                .hasMessage("AUTH_INVALID_CREDENTIALS");
        assertThat(failing.metadata()).allSatisfy(value -> assertThat(value)
                .doesNotContain("Password1", "WrongPass1", "hash=", "salt=", "token="));
    }

    @Test
    void failureAuditOutageCannotReplaceStableAdministratorErrors() {
        ThrowingAudits failing = new ThrowingAudits(false);
        UserService subject = service(failing);
        ClientContext context = new ClientContext("admin", "127.0.0.1");

        assertThatThrownBy(() -> subject.updateRole(ADMIN_ID,
                new UpdateUserRoleCommand(ADMIN_ID, UserRole.TEACHER, 0), context))
                .hasMessage("USER_LAST_ADMIN_PROTECTED");
        assertThatThrownBy(() -> subject.changeStatus(ADMIN_ID,
                new ChangeUserStatusCommand(ADMIN_ID, AccountStatus.PENDING, "invalid", 0),
                context)).hasMessage("USER_STATUS_CONFLICT");
    }

    @Test
    void successAuditOutageRollsBackBusinessWrite() {
        UserService subject = service(new ThrowingAudits(true));

        assertThatThrownBy(() -> subject.applyForTeacherAccount(
                new TeacherAccountApplicationCommand("TEACHER02", "Password1".toCharArray())))
                .hasMessageContaining("audit storage unavailable");
        Optional<UserAccount> stored = transactions.inTransaction(connection ->
                users.findByNormalizedLoginId(connection, "TEACHER02"));
        assertThat(stored).isEmpty();
    }

    private UserService service(AuditRepository audits) {
        return new UserServiceImpl(transactions, new StripedResourceLockManager(), users,
                audits, hasher);
    }

    private void insert(UserAccount account) {
        transactions.inTransaction(connection -> { users.insert(connection, account); return null; });
    }

    private UserAccount account(String loginId, UserRole role) {
        PasswordHash password = hasher.hash("Pass1234".toCharArray());
        LocalDateTime now = LocalDateTime.now();
        return new UserAccount(UUID.randomUUID().toString(), loginId, password.hash(),
                password.salt(), password.iterations(), role, AccountStatus.ACTIVE, false,
                0, null, null, 0, now, now);
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule : Path.of("vcampus-database", folder, name);
    }

    private static void execute(Connection connection, Path path) throws Exception {
        for (String sql : Files.readString(path).split(";")) {
            if (!sql.isBlank()) try (var statement = connection.createStatement()) {
                statement.execute(sql.strip());
            }
        }
    }

    private static final class ThrowingAudits implements AuditRepository {
        private final boolean failSuccess;
        private final List<String> metadata = new ArrayList<>();

        private ThrowingAudits(boolean failSuccess) { this.failSuccess = failSuccess; }

        @Override public void record(Connection connection, String actor, String action,
                String targetType, String target, String result, String address) {
            metadata.add(String.join("|", String.valueOf(actor), action, targetType,
                    String.valueOf(target), result, String.valueOf(address)));
            if (failSuccess == "SUCCESS".equals(result)) {
                throw new IllegalStateException("audit storage unavailable: "
                        + "password=Secret123 hash=secret salt=secret token=secret");
            }
        }

        List<String> metadata() { return List.copyOf(metadata); }
    }
}
