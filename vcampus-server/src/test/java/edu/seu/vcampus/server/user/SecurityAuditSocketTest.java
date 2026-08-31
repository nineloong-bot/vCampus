package edu.seu.vcampus.server.user;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.SecurityAuditView;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.handler.SecurityAuditHandler;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.service.SecurityAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditSocketTest {
    private SocketServer server;
    private ExecutorService executor;
    private Future<?> serving;

    @AfterEach
    void stopServer() throws Exception {
        if (server != null) { server.stopAccepting(); server.awaitRequests(Duration.ofSeconds(2)); }
        if (serving != null) serving.get();
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void returnsTypedSanitizedAuditPageOverRealSocket() throws Exception {
        Path data = Path.of("target", "test-data", UUID.randomUUID() + ".accdb");
        Files.createDirectories(data.getParent());
        String url = "jdbc:ucanaccess://" + data
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider connections = () -> DriverManager.getConnection(url);
        try (var connection = connections.open()) {
            execute(connection, projectFile("schema", "010_user.sql"));
            execute(connection, projectFile("seed", "010_roles_permissions.sql"));
        }
        TransactionManager transactions = new TransactionManager(connections);
        AccessAuditRepository repository = new AccessAuditRepository();
        transactions.inTransaction(connection -> {
            repository.record(connection, null, "USER_LOGIN", "USER", "target",
                    "AUTH_INVALID_CREDENTIALS", "sensitive-address");
            return null;
        });
        SessionRegistry sessions = new SessionRegistry();
        String token = sessions.create(new UserIdentity("admin", "ADMIN", UserRole.ADMIN,
                AccountStatus.ACTIVE), Set.of("USER_AUDIT_READ"), false, "client");
        SecurityAuditHandler handler = new SecurityAuditHandler(
                new AuthorizationService(sessions),
                new SecurityAuditService(transactions, repository));
        server = new SocketServer(0, 2, 5,
                new MessageRouter(Map.of("SECURITY_AUDIT_SEARCH", handler)));
        executor = Executors.newSingleThreadExecutor();
        serving = executor.submit(() -> { server.serve(); return null; });

        Message response = exchange(token);

        assertThat(response.body()).isInstanceOf(ResponseBody.class);
        ResponseBody<?> body = (ResponseBody<?>) response.body();
        assertThat(body.success()).isTrue();
        assertThat(body.data()).isInstanceOf(PageResult.class);
        Object item = ((PageResult<?>) body.data()).items().getFirst();
        assertThat(item).isInstanceOf(SecurityAuditView.class);
        assertThat(item.toString()).doesNotContain("sensitive-address");
    }

    private Message exchange(String token) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.localPort());
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
            output.flush();
            try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                output.writeObject(new Message(UUID.randomUUID().toString(), MessageType.REQUEST,
                        "SECURITY_AUDIT_SEARCH", token, new SecurityAuditQuery(
                        null, null, null, null, null, 0, 20), System.currentTimeMillis()));
                output.flush(); return (Message) input.readObject();
            }
        }
    }
    private static Path projectFile(String folder, String name) {
        Path module = Path.of("..", "vcampus-database", folder, name);
        return Files.exists(module) ? module : Path.of("vcampus-database", folder, name);
    }
    private static void execute(java.sql.Connection connection, Path path) throws Exception {
        for (String sql : Files.readString(path).split(";")) if (!sql.isBlank()) {
            try (var statement = connection.createStatement()) { statement.execute(sql.strip()); }
        }
    }
}
