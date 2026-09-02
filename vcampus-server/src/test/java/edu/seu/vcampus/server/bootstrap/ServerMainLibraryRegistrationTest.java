package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.common.library.BookSearchQuery;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.server.config.ServerConfig;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ServerMainLibraryRegistrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void applicationRouterRegistersLibraryCommandsAgainstTheSharedSessionRegistry()
            throws Exception {
        ServerConfig config = new ServerConfig(8888, 10, 2,
                temporaryDirectory.resolve("unified.accdb"),
                databaseRoot(), true, 7, 15, 24);
        Method factory = ServerMain.class.getDeclaredMethod("createApplicationRouter",
                ServerConfig.class);
        factory.setAccessible(true);
        MessageRouter router = (MessageRouter) factory.invoke(null, config);
        Message request = new Message("request-1", MessageType.REQUEST,
                "LIBRARY_SEARCH_BOOKS", null,
                new BookSearchQuery("", "", false, 0, 20), 1L);

        var response = router.route(request,
                new ClientContext("connection-1", "127.0.0.1"));
        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("AUTH_SESSION_EXPIRED");
    }

    private static Path databaseRoot() {
        Path root = Path.of("vcampus-database");
        return (Files.exists(root) ? root : Path.of("..", "vcampus-database"))
                .toAbsolutePath().normalize();
    }
}
