package edu.seu.vcampus.server.user;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.server.bootstrap.ApplicationRuntime;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.routing.ClientContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ServerMainAuditAssemblyTest {
    @Test
    void unifiedProductionRuntimeKeepsSecurityAuditAssemblyAlongsideCourseRouting() throws Exception {
        Path database = Files.createTempDirectory("vcampus-audit-runtime-").resolve("runtime.accdb");
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");
        ApplicationRuntime runtime = ApplicationRuntime.create(connections, databaseRoot(), Clock.systemUTC());

        ResponseBody<?> response = runtime.router().route(new Message(UUID.randomUUID().toString(),
                MessageType.REQUEST, "SECURITY_AUDIT_SEARCH", null,
                new SecurityAuditQuery(null, null, null, null, null, 0, 20), 0),
                new ClientContext("connection", "127.0.0.1"));
        assertThat(response.code()).isEqualTo("AUTH_SESSION_EXPIRED");
    }

    @Test
    void unifiedProductionRuntimeReplaysAUserWriteWithTheSameRequestId() throws Exception {
        Path database = Files.createTempDirectory("vcampus-dedup-runtime-").resolve("runtime.accdb");
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");
        ApplicationRuntime runtime = ApplicationRuntime.create(connections, databaseRoot(), Clock.systemUTC());
        String requestId = UUID.randomUUID().toString();

        ResponseBody<?> first = register(runtime, requestId, "TEACHER_DEDUP");
        ResponseBody<?> replay = register(runtime, requestId, "TEACHER_DEDUP");

        assertThat(first.success()).isTrue();
        assertThat(replay.success()).isTrue();
    }

    private static ResponseBody<?> register(ApplicationRuntime runtime, String requestId, String loginId) {
        return runtime.router().route(new Message(requestId, MessageType.REQUEST, "USER_REGISTER", null,
                new TeacherAccountApplicationCommand(loginId, "Password1".toCharArray()), 0),
                new ClientContext("connection", "127.0.0.1"));
    }

    private static Path databaseRoot() {
        Path root = Path.of("vcampus-database");
        return Files.exists(root) ? root : Path.of("..", "vcampus-database");
    }
}
