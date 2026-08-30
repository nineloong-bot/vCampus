package edu.seu.vcampus.server.user;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.handler.UserLoginHandler;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;
import edu.seu.vcampus.server.user.service.PasswordHasher;
import edu.seu.vcampus.server.user.service.UserService;
import edu.seu.vcampus.server.user.service.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;

class UserLoginSocketDemoTest {
    private static final String LOGIN_ID = "DEMO_ADMIN";
    private SocketServer server;
    private ExecutorService serverThread;
    private Future<?> serving;
    private char[] demoPassword;
    private TransactionManager transactions;

    @BeforeEach
    void startSocketServerWithIsolatedAccessDatabase() throws Exception {
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
        UserRepository users = new AccessUserRepository();
        demoPassword = randomPassword("Demo", '7');
        insertDemoAdministrator(transactions, users);
        UserService service = new UserServiceImpl(transactions,
                new StripedResourceLockManager(), users,
                new AccessAuditRepository(), new PasswordHasher());
        MessageRouter router = new MessageRouter(Map.of(
                "USER_LOGIN", new UserLoginHandler(service)));
        server = new SocketServer(0, 2, 10, router);
        serverThread = Executors.newSingleThreadExecutor();
        serving = serverThread.submit(() -> {
            server.serve();
            return null;
        });
    }

    @AfterEach
    void stopSocketServerAndClearFixturePassword() throws Exception {
        if (server != null) {
            server.stopAccepting();
            assertThat(server.awaitRequests(Duration.ofSeconds(2))).isTrue();
        }
        if (serving != null) {
            serving.get();
        }
        if (serverThread != null) {
            serverThread.shutdownNow();
        }
        if (demoPassword != null) {
            Arrays.fill(demoPassword, '\0');
        }
    }

    @Test
    void sendsUserLoginOverRealSocketAndReceivesLoginResult() throws Exception {
        char[] submitted = demoPassword.clone();
        LoginCommand command = new LoginCommand("demo_admin", submitted, "demo-client");

        Message response = exchange(command);

        assertThat(submitted).containsOnly('\0');
        assertThat(response.type()).isEqualTo(MessageType.RESPONSE);
        assertThat(response.body()).isInstanceOf(ResponseBody.class);
        ResponseBody<?> body = (ResponseBody<?>) response.body();
        assertThat(body.success()).isTrue();
        assertThat(body.data()).isInstanceOf(LoginResult.class);
        LoginResult result = (LoginResult) body.data();
        assertThat(result.user().loginId()).isEqualTo(LOGIN_ID);
    }

    @Test
    void returnsInvalidCredentialsWithoutDisconnectingSocket() throws Exception {
        char[] submitted = randomPassword("Wrong", '9');
        LoginCommand command = new LoginCommand(LOGIN_ID, submitted, "demo-client");

        Message response = exchange(command);

        assertThat(submitted).containsOnly('\0');
        assertThat(response.type()).isEqualTo(MessageType.RESPONSE);
        assertThat(response.body()).isInstanceOf(ResponseBody.class);
        ResponseBody<?> body = (ResponseBody<?>) response.body();
        assertThat(body.success()).isFalse();
        assertThat(body.code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void malformedLoginBodyReturnsFailureAndKeepsTheSocketUsable() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.localPort())) {
            socket.setSoTimeout(3_000);
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            try (output; ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                output.writeObject(request(EmptyRequest.INSTANCE));
                output.flush();

                Message malformedResponse = (Message) input.readObject();
                assertThat(malformedResponse.body()).isInstanceOf(ResponseBody.class);
                ResponseBody<?> failure = (ResponseBody<?>) malformedResponse.body();
                assertThat(failure).extracting(body -> body.success(), body -> body.code())
                        .containsExactly(false, "COMMON_VALIDATION_FAILED");
                assertMalformedLoginAudit();

                output.writeObject(request(new LoginCommand(LOGIN_ID, demoPassword.clone(),
                        "demo-client")));
                output.flush();

                Message validResponse = (Message) input.readObject();
                assertThat(((ResponseBody<?>) validResponse.body()).success()).isTrue();
            }
        }
    }

    private void assertMalformedLoginAudit() {
        transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT userId,targetId,resultCode FROM tblAuditLog "
                            + "WHERE actionCode='USER_LOGIN' AND resultCode='COMMON_VALIDATION_FAILED'")) {
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isNull();
                    assertThat(rows.getString(2)).isNull();
                    assertThat(rows.getString(3)).isEqualTo("COMMON_VALIDATION_FAILED");
                    assertThat(rows.next()).isFalse();
                }
            }
            return null;
        });
    }

    private Message exchange(LoginCommand command) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.localPort())) {
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            try (output;
                 ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                Message request = new Message(UUID.randomUUID().toString(), MessageType.REQUEST,
                        "USER_LOGIN", null, command, System.currentTimeMillis());
                output.writeObject(request);
                output.flush();
                return (Message) input.readObject();
            }
        }
    }

    private static Message request(java.io.Serializable body) {
        return new Message(UUID.randomUUID().toString(), MessageType.REQUEST,
                "USER_LOGIN", null, body, System.currentTimeMillis());
    }

    private void insertDemoAdministrator(
            TransactionManager transactions, UserRepository users) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        PBEKeySpec specification = new PBEKeySpec(demoPassword, salt, 120_000, 256);
        byte[] hash;
        try {
            hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification).getEncoded();
        } finally {
            specification.clearPassword();
        }
        LocalDateTime now = LocalDateTime.now();
        UserAccount account = new UserAccount(UUID.randomUUID().toString(), LOGIN_ID,
                Base64.getEncoder().encodeToString(hash),
                Base64.getEncoder().encodeToString(salt), 120_000,
                ADMIN, ACTIVE, false, 0, null, null, 0, now, now);
        transactions.inTransaction(connection -> {
            users.insert(connection, account);
            return null;
        });
    }

    private static char[] randomPassword(String prefix, char suffix) {
        return (prefix + UUID.randomUUID().toString().replace("-", "") + suffix)
                .toCharArray();
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
