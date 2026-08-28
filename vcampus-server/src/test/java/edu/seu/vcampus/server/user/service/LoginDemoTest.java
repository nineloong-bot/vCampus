package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginDemoTest {
    private static final String LOGIN_ID = "DEMO_ADMIN";
    private TransactionManager transactions;
    private UserRepository users;
    private UserService service;
    private char[] demoPassword;

    @BeforeEach
    void createDemoAdministratorInIsolatedAccessDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(provider);
        try (Connection connection = provider.open()) {
            executeScript(connection, projectFile("schema", "010_user.sql"));
            executeScript(connection, projectFile("seed", "010_roles_permissions.sql"));
        }
        users = new AccessUserRepository();
        PasswordHasher hasher = new PasswordHasher();
        demoPassword = ("Demo" + UUID.randomUUID().toString().replace("-", "") + "7")
                .toCharArray();
        PasswordHash passwordHash = hasher.hash(demoPassword);
        LocalDateTime now = LocalDateTime.now();
        UserAccount account = new UserAccount(
                UUID.randomUUID().toString(), LOGIN_ID,
                passwordHash.hash(), passwordHash.salt(), passwordHash.iterations(),
                ADMIN, ACTIVE, false, 0, null, null, 0, now, now);
        transactions.inTransaction(connection -> {
            users.insert(connection, account);
            return null;
        });
        service = new UserServiceImpl(transactions, new StripedResourceLockManager(),
                users, new AccessAuditRepository(), hasher);
    }

    @AfterEach
    void clearFixturePassword() {
        if (demoPassword != null) {
            Arrays.fill(demoPassword, '\0');
        }
    }

    @Test
    void logsInActiveAdministratorWithPbkdf2Credential() {
        UserAccount stored = findUser(LOGIN_ID);
        assertThat(stored.role()).isEqualTo(ADMIN);
        assertThat(stored.accountStatus()).isEqualTo(ACTIVE);
        assertThat(stored.mustChangePassword()).isFalse();
        assertThat(stored.passwordHash()).isNotEqualTo(new String(demoPassword));
        assertThat(stored.passwordSalt()).isNotBlank();
        assertThat(stored.passwordIterations()).isEqualTo(120_000);
        char[] submitted = demoPassword.clone();

        LoginResult result = service.login(
                new LoginCommand("demo_admin", submitted, "demo-client"),
                new ClientContext("connection-1", "127.0.0.1"));

        assertThat(result.sessionToken()).isNotBlank();
        assertThat(result.user().loginId()).isEqualTo(LOGIN_ID);
        assertThat(result.user().role()).isEqualTo(ADMIN);
        assertThat(result.user().accountStatus()).isEqualTo(ACTIVE);
        assertThat(result.user().mustChangePassword()).isFalse();
        assertThat(result.mustChangePassword()).isFalse();
        assertThat(submitted).containsOnly('\0');
    }

    @Test
    void rejectsWrongPasswordAndClearsSubmittedCharacters() {
        char[] submitted = ("Wrong" + UUID.randomUUID().toString().replace("-", "") + "9")
                .toCharArray();

        assertThatThrownBy(() -> service.login(
                new LoginCommand(LOGIN_ID, submitted, "demo-client"),
                new ClientContext("connection-2", "127.0.0.1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("AUTH_INVALID_CREDENTIALS");

        assertThat(submitted).containsOnly('\0');
    }

    @Test
    void rejectsUnknownLoginIdAndClearsSubmittedCharacters() {
        char[] submitted = demoPassword.clone();

        assertThatThrownBy(() -> service.login(
                new LoginCommand("MISSING_USER", submitted, "demo-client"),
                new ClientContext("connection-3", "127.0.0.1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("AUTH_INVALID_CREDENTIALS");

        assertThat(submitted).containsOnly('\0');
    }

    private UserAccount findUser(String loginId) {
        return transactions.inTransaction(connection ->
                users.findByNormalizedLoginId(connection, loginId).orElseThrow());
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
}
