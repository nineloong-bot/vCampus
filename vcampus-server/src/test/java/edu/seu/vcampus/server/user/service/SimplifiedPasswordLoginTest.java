package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.ChangePasswordCommand;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.UUID;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;

class SimplifiedPasswordLoginTest {
    private TransactionManager transactions;
    private UserService service;

    @BeforeEach
    void setupIsolatedDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(provider);
        try (Connection connection = provider.open()) {
            executeScript(connection, projectFile("schema", "010_user.sql"));
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO tblRole (roleCode, roleName) VALUES ('ADMIN', '系统管理员')");
                statement.execute("INSERT INTO tblRole (roleCode, roleName) VALUES ('STUDENT_ADMIN', '学籍管理员')");
            }
        }
        UserRepository users = new AccessUserRepository();
        PasswordHasher hasher = new PasswordHasher();
        LocalDateTime now = LocalDateTime.now();

        // Super admin with mustChangePassword=false
        UserAccount admin = new UserAccount(
                UUID.randomUUID().toString(), "ADMIN",
                "complexHash", "salt", 120_000,
                ADMIN, ACTIVE, false, 0, null, null, 0, now, now);

        // Student admin with mustChangePassword=true
        UserAccount stuAdmin = new UserAccount(
                UUID.randomUUID().toString(), "STUDENT_ADMIN",
                "complexHash", "salt", 120_000,
                edu.seu.vcampus.common.user.UserRole.STUDENT_ADMIN, ACTIVE, true, 0, null, null, 0, now, now);

        transactions.inTransaction(connection -> {
            users.insert(connection, admin);
            users.insert(connection, stuAdmin);
            return null;
        });

        service = new UserServiceImpl(transactions, new StripedResourceLockManager(),
                users, new AccessAuditRepository(), hasher);
    }

    @Test
    void allowsLoginWithShortAliasAndSimplifiedPassword() {
        LoginResult adminResult = service.login(
                new LoginCommand("admin", "123456".toCharArray(), "demo-client"),
                new ClientContext("conn-1", "127.0.0.1"));
        assertThat(adminResult.user().loginId()).isEqualTo("ADMIN");
        assertThat(adminResult.mustChangePassword()).isFalse();

        LoginResult stuResult = service.login(
                new LoginCommand("stu", "123456".toCharArray(), "demo-client"),
                new ClientContext("conn-2", "127.0.0.1"));
        assertThat(stuResult.user().loginId()).isEqualTo("STUDENT_ADMIN");
        assertThat(stuResult.mustChangePassword()).isTrue();
    }

    @Test
    void passwordAcceptedForRestrictedLoginCanCompleteInitialPasswordChange() {
        LoginResult restricted = service.login(
                new LoginCommand("stu", "123456".toCharArray(), "demo-client"),
                new ClientContext("conn-1", "127.0.0.1"));

        service.changePassword(restricted.sessionToken(),
                new ChangePasswordCommand(
                        "123456".toCharArray(), "Replacement8".toCharArray()));

        LoginResult changed = service.login(
                new LoginCommand("stu", "Replacement8".toCharArray(), "demo-client"),
                new ClientContext("conn-2", "127.0.0.1"));
        assertThat(changed.mustChangePassword()).isFalse();
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
