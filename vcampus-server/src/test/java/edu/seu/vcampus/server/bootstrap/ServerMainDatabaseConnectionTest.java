package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.config.ServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ServerMainDatabaseConnectionTest {
    @TempDir Path temporaryDirectory;

    @Test
    void productionDatabaseUrlSupportsConnectionsOpenedAndClosedByWorkerThreads() throws Exception {
        ServerConfig config = new ServerConfig(8888, 10, 4,
                temporaryDirectory.resolve("vcampus.accdb"), temporaryDirectory, true,
                30, 15, 24);
        String url = ServerMain.databaseUrl(config);
        assertThat(url).doesNotContain("immediatelyReleaseResources");
        try (var ignored = DriverManager.getConnection(url)) {
            // Create the Access file before concurrent worker access.
        }

        CompletableFuture<?>[] workers = IntStream.range(0, 4)
                .mapToObj(worker -> CompletableFuture.runAsync(() -> {
                    for (int attempt = 0; attempt < 3; attempt++) {
                        try (var connection = DriverManager.getConnection(url)) {
                            assertThat(connection.isValid(2)).isTrue();
                        } catch (Exception error) {
                            throw new AssertionError(error);
                        }
                    }
                }))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(workers).get(20, TimeUnit.SECONDS);
    }
}
