package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.security.InvalidCredentialsException;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessPermissionRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.STUDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionReplacementTest {
    private TransactionManager transactions;
    private UserRepository users;
    private PasswordHasher hasher;
    private SessionRegistry sessions;
    private UserService service;
    private char[] password;

    @BeforeEach
    void setUp() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> java.sql.DriverManager.getConnection(url);
        transactions = new TransactionManager(provider);
        try (Connection connection = provider.open()) {
            executeScript(connection, projectFile("schema", "010_user.sql"));
            executeScript(connection, projectFile("seed", "010_roles_permissions.sql"));
        }
        users = new AccessUserRepository();
        hasher = new PasswordHasher();
        sessions = new SessionRegistry();
        password = "Password1".toCharArray();
        insert("alice-id", "ALICE");
        insert("bob-id", "BOB");
        service = service(new AccessAuditRepository());
    }

    @AfterEach
    void clearPassword() {
        Arrays.fill(password, '\0');
    }

    @Test
    void laterSuccessfulLoginInvalidatesOnlyTheSameUsersPreviousSessionAndAuditsReplacement() {
        LoginResult tokenA = login(service, "ALICE", "client-a", "127.0.0.1", password);
        LoginResult tokenBUser = login(service, "BOB", "client-b", "127.0.0.2", password);
        assertThat(countReplacementAudits()).isZero();

        LoginResult tokenB = login(service, "ALICE", "client-c", "127.0.0.3", password);

        assertThatThrownBy(() -> service.getCurrentUser(tokenA.sessionToken()))
                .isInstanceOf(SessionExpiredException.class);
        assertThat(service.getCurrentUser(tokenB.sessionToken()).loginId()).isEqualTo("ALICE");
        assertThat(service.getCurrentUser(tokenBUser.sessionToken()).loginId()).isEqualTo("BOB");
        assertThat(countReplacementAudits()).isEqualTo(1);
        assertReplacementAuditIsSanitized(tokenA.sessionToken(), tokenB.sessionToken());
    }

    @Test
    void failedLoginDoesNotInvalidateExistingSessionOrWriteReplacementAudit() {
        LoginResult existing = login(service, "ALICE", "client-a", "127.0.0.1", password);

        assertThatThrownBy(() -> login(service, "ALICE", "client-b", "127.0.0.2",
                "WrongPassword7".toCharArray()))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(service.getCurrentUser(existing.sessionToken()).loginId()).isEqualTo("ALICE");
        assertThat(countReplacementAudits()).isZero();
    }

    @Test
    void replacementAuditFailureDoesNotTurnTheNewSuccessfulLoginIntoFailure() {
        UserService auditFailingService = service(new ReplacementAuditFailureRepository());
        LoginResult first = login(auditFailingService, "ALICE", "client-a", "127.0.0.1",
                password);

        LoginResult second = login(auditFailingService, "ALICE", "client-b", "127.0.0.2",
                password);

        assertThat(second.sessionToken()).isNotBlank();
        assertThatThrownBy(() -> auditFailingService.getCurrentUser(first.sessionToken()))
                .isInstanceOf(SessionExpiredException.class);
        assertThat(auditFailingService.getCurrentUser(second.sessionToken()).loginId())
                .isEqualTo("ALICE");
    }

    private UserService service(AuditRepository audits) {
        return new UserServiceImpl(transactions, new StripedResourceLockManager(), users,
                new AccessPermissionRepository(), audits, hasher, sessions, Clock.systemUTC());
    }

    private LoginResult login(UserService target, String loginId, String clientInstanceId,
                              String address, char[] credential) {
        char[] submitted = credential.clone();
        return target.login(new LoginCommand(loginId, submitted, clientInstanceId),
                new ClientContext(UUID.randomUUID().toString(), address));
    }

    private void insert(String userId, String loginId) {
        PasswordHash hash = hasher.hash(password);
        LocalDateTime now = LocalDateTime.now();
        transactions.inTransaction(connection -> {
            users.insert(connection, new UserAccount(userId, loginId, hash.hash(), hash.salt(),
                    hash.iterations(), STUDENT, ACTIVE, false, 0, null, null, 0, now, now));
            return null;
        });
    }

    private long countReplacementAudits() {
        return transactions.inTransaction(connection -> {
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM tblAuditLog "
                         + "WHERE actionCode='USER_SESSION_REPLACED'")) {
                rows.next();
                return rows.getLong(1);
            } catch (java.sql.SQLException error) {
                throw new AssertionError(error);
            }
        });
    }

    private void assertReplacementAuditIsSanitized(String oldToken, String newToken) {
        transactions.inTransaction(connection -> {
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT userId, targetId, resultCode, "
                         + "clientAddress FROM tblAuditLog "
                         + "WHERE actionCode='USER_SESSION_REPLACED'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("userId")).isEqualTo("alice-id");
                assertThat(rows.getString("targetId")).isEqualTo("alice-id");
                assertThat(rows.getString("resultCode")).isEqualTo("SUCCESS");
                String safeRow = rows.getString("userId") + rows.getString("targetId")
                        + rows.getString("resultCode") + rows.getString("clientAddress");
                assertThat(safeRow).doesNotContain(oldToken, newToken, "Password1", "client-c",
                        "passwordHash", "passwordSalt");
                assertThat(rows.next()).isFalse();
            } catch (java.sql.SQLException error) {
                throw new AssertionError(error);
            }
            return null;
        });
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule
                : Path.of("vcampus-database", folder, name);
    }

    private static void executeScript(Connection connection, Path path) throws Exception {
        for (String sql : Files.readString(path).split(";")) {
            if (!sql.isBlank()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(sql.strip());
                }
            }
        }
    }

    private static final class ReplacementAuditFailureRepository implements AuditRepository {
        private final AuditRepository delegate = new AccessAuditRepository();

        @Override
        public void record(Connection connection, String actorUserId, String actionCode,
                           String targetType, String targetId, String resultCode,
                           String clientAddress) {
            if ("USER_SESSION_REPLACED".equals(actionCode)) {
                throw new IllegalStateException("simulated replacement audit failure");
            }
            delegate.record(connection, actorUserId, actionCode, targetType, targetId,
                    resultCode, clientAddress);
        }
    }
}
