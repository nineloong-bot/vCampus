package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.security.InvalidCredentialsException;
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
import java.time.LocalDateTime;
import java.util.UUID;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.STUDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordChangeTest {
    private UserService service;
    private SessionRegistry sessions;

    @BeforeEach
    void setUp() throws Exception {
        Path data = Path.of("target", "test-data");
        Files.createDirectories(data);
        String url = "jdbc:ucanaccess://" + data.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider connections = () -> DriverManager.getConnection(url);
        TransactionManager transactions = new TransactionManager(connections);
        try (var connection = connections.open()) {
            for (Path script : new Path[] {projectFile("schema", "010_user.sql"),
                    projectFile("seed", "010_roles_permissions.sql")}) {
                executeScript(connection, script);
            }
        }
        UserRepository users = new AccessUserRepository();
        PasswordHasher hasher = new PasswordHasher();
        PasswordHash hash = hasher.hash("12345678".toCharArray());
        LocalDateTime now = LocalDateTime.now();
        transactions.inTransaction(connection -> {
            users.insert(connection, new UserAccount("student-id", "213242478", hash.hash(),
                    hash.salt(), hash.iterations(), STUDENT, ACTIVE, true, 0, null, null,
                    0, now, now));
            return null;
        });
        sessions = new SessionRegistry();
        service = new UserServiceImpl(transactions, new StripedResourceLockManager(), users,
                new AccessAuditRepository(), hasher, sessions, java.time.Clock.systemUTC());
    }

    @Test
    void changingPasswordRevokesRestrictedSessionAndInvalidatesOldPassword() {
        String token = login("12345678").sessionToken();
        String secondToken = login("12345678").sessionToken();

        service.changePassword(token,
                new ChangePasswordCommand("12345678".toCharArray(), "NewPass123".toCharArray()));

        assertThatThrownBy(() -> sessions.requireSession(token))
                .isInstanceOf(SessionExpiredException.class);
        assertThatThrownBy(() -> sessions.requireSession(secondToken))
                .isInstanceOf(SessionExpiredException.class);
        assertThatThrownBy(() -> login("12345678"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(login("NewPass123").mustChangePassword()).isFalse();
    }

    private edu.seu.vcampus.common.user.LoginResult login(String password) {
        return service.login(new LoginCommand("213242478", password.toCharArray(), "client"),
                new ClientContext("connection", "127.0.0.1"));
    }

    private static Path projectFile(String folder, String name) {
        Path fromModule = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(fromModule) ? fromModule : Path.of("vcampus-database", folder, name);
    }

    private static void executeScript(java.sql.Connection connection, Path path) throws Exception {
        for (String sql : Files.readString(path).split(";")) {
            if (!sql.isBlank()) {
                try (var statement = connection.createStatement()) { statement.execute(sql.strip()); }
            }
        }
    }
}
