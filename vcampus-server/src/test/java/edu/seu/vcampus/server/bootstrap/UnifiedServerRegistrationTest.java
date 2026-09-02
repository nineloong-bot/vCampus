package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.persistence.ConnectionProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedServerRegistrationTest {
    @Test
    void registersEveryCampusModuleOnOneRouter() throws Exception {
        Path database = Files.createTempDirectory("vcampus-unified-runtime-")
                .resolve("runtime.accdb");
        ConnectionProvider connections = () -> DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";newDatabaseVersion=V2010");

        ApplicationRuntime runtime = ApplicationRuntime.create(
                connections, databaseRoot(), Clock.systemUTC());

        assertThat(runtime.router().isRegistered("USER_LOGIN")).isTrue();
        assertThat(runtime.router().isRegistered("STUDENT_GET_CURRENT")).isTrue();
        assertThat(runtime.router().isRegistered("COURSE_SEARCH_OFFERINGS")).isTrue();
        assertThat(runtime.router().isRegistered("LIBRARY_SEARCH_BOOKS")).isTrue();
        assertThat(runtime.router().isRegistered("SHOP_HOME")).isTrue();
    }

    private static Path databaseRoot() {
        Path root = Path.of("vcampus-database");
        return Files.exists(root) ? root : Path.of("..", "vcampus-database");
    }
}
