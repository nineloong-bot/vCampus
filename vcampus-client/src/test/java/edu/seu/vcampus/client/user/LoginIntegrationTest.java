package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.user.AccessDatabase;
import edu.seu.vcampus.server.user.LoginService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class LoginIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsDatabaseAndAuthenticatesAcrossSocket() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("vCampus.accdb");
        AccessDatabase database = new AccessDatabase(databaseFile);
        database.initialize();
        LoginService loginService = new LoginService(database);
        MessageRouter router = new MessageRouter(Map.of("USER_LOGIN", loginService::login));

        try (SocketServer server = new SocketServer(0, 2, 10, router);
             var serverThread = Executors.newSingleThreadExecutor();
             ClientConnection client = new ClientConnection("127.0.0.1", server.localPort())) {
            var serving = serverThread.submit(() -> {
                server.serve();
                return null;
            });
            client.connect(Duration.ofSeconds(2));

            ResponseBody<LoginResult> rejected = login(client, "wrong-password");
            ResponseBody<LoginResult> accepted = login(client, AccessDatabase.DEMO_PASSWORD);

            assertThat(Files.isRegularFile(databaseFile)).isTrue();
            assertThat(rejected.success()).isFalse();
            assertThat(rejected.code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
            assertThat(accepted.success()).isTrue();
            assertThat(accepted.data().sessionToken()).isNotBlank();
            assertThat(accepted.data().user().loginId()).isEqualTo(AccessDatabase.DEMO_LOGIN_ID);
            assertThat(accepted.data().user().role()).isEqualTo(UserRole.ADMIN);

            client.close();
            server.stopAccepting();
            assertThat(server.awaitRequests(Duration.ofSeconds(2))).isTrue();
            serving.get();
        }
    }

    private static ResponseBody<LoginResult> login(ClientConnection client, String password)
            throws Exception {
        return client.<LoginResult>send("USER_LOGIN",
                        new LoginCommand("admin", password.toCharArray(),
                                UUID.randomUUID().toString()),
                        Duration.ofSeconds(3))
                .get();
    }
}
