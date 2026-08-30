package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.security.AccountLockedException;
import edu.seu.vcampus.server.security.InvalidCredentialsException;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.STUDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginLockoutTest {
    private MutableClock clock;
    private UserService service;
    private TransactionManager transactions;
    private UserRepository users;

    @BeforeEach
    void setUp() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider connections = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(connections);
        try (var connection = connections.open()) {
            executeScript(connection, projectFile("schema", "010_user.sql"));
            executeScript(connection, projectFile("seed", "010_roles_permissions.sql"));
        }
        users = new AccessUserRepository();
        PasswordHasher hasher = new PasswordHasher();
        PasswordHash password = hasher.hash("Password1".toCharArray());
        LocalDateTime now = LocalDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
        transactions.inTransaction(connection -> {
            users.insert(connection, new UserAccount("alice-id", "ALICE", password.hash(),
                    password.salt(), password.iterations(), STUDENT, ACTIVE, false, 0,
                    null, null, 0, now, now));
            return null;
        });
        clock = new MutableClock();
        service = new UserServiceImpl(transactions, new StripedResourceLockManager(), users,
                new AccessAuditRepository(), hasher, new SessionRegistry(clock), clock);
    }

    @Test
    void fifthInvalidPasswordLocksAccountForFifteenMinutes() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> login("wrong"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> login("Password1"))
                .isInstanceOf(AccountLockedException.class);
        clock.advance(Duration.ofMinutes(15));

        assertThat(login("Password1").sessionToken()).isNotBlank();
    }

    @Test
    void failedLoginIncrementsOnlyTheFailureState() {
        assertThatThrownBy(() -> login("wrong")).isInstanceOf(InvalidCredentialsException.class);

        UserAccount account = transactions.inTransaction(connection ->
                users.findByNormalizedLoginId(connection, "ALICE").orElseThrow());

        assertThat(account.failedLoginCount()).isEqualTo(1);
        assertThat(account.lastLoginAt()).isNull();
        assertThat(account.lockedUntil()).isNull();
    }

    @Test
    void wrongPasswordDuringLockDoesNotExtendTheLockoutWindow() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> login("wrong")).isInstanceOf(InvalidCredentialsException.class);
        }
        clock.advance(Duration.ofMinutes(10));
        assertThatThrownBy(() -> login("wrong")).isInstanceOf(InvalidCredentialsException.class);
        clock.advance(Duration.ofMinutes(5));

        assertThat(login("Password1").sessionToken()).isNotBlank();
    }

    @Test
    void successfulLoginAfterLockoutResetsFailureCount() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> login("wrong")).isInstanceOf(InvalidCredentialsException.class);
        }
        clock.advance(Duration.ofMinutes(15));

        login("Password1");

        UserAccount account = transactions.inTransaction(connection ->
                users.findByNormalizedLoginId(connection, "ALICE").orElseThrow());
        assertThat(account.failedLoginCount()).isZero();
        assertThat(account.lockedUntil()).isNull();
    }

    private edu.seu.vcampus.common.user.LoginResult login(String password) {
        return service.login(new LoginCommand("alice", password.toCharArray(), "client"),
                new ClientContext("connection", "127.0.0.1"));
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule : Path.of("vcampus-database", folder, name);
    }

    private static void executeScript(java.sql.Connection connection, Path path) throws Exception {
        for (String sql : Files.readString(path).split(";")) {
            if (!sql.isBlank()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(sql.strip());
                }
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
    }
}
