package edu.seu.vcampus.server.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerConfigTest {
    private final Path baseDirectory = Path.of("target", "config-test").toAbsolutePath();

    @BeforeEach
    void createDatabase() throws Exception {
        Files.createDirectories(baseDirectory.resolve("data"));
        Files.write(baseDirectory.resolve("data/vCampus.accdb"), new byte[] {1});
    }

    @Test
    void resolvesAndValidatesConfiguration() {
        ServerConfig config = ServerConfig.from(validProperties(), baseDirectory);

        assertThat(config.port()).isEqualTo(8888);
        assertThat(config.databasePath()).isEqualTo(
                baseDirectory.resolve("data/vCampus.accdb").normalize());
        assertThat(config.workerThreads()).isEqualTo(8);
    }

    @Test
    void rejectsInvalidPort() {
        Properties properties = validProperties();
        properties.setProperty("server.port", "70000");

        assertThatThrownBy(() -> ServerConfig.from(properties, baseDirectory))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("server.port");
    }

    @Test
    void rejectsMissingDatabase() {
        Properties properties = validProperties();
        properties.setProperty("database.path", "data/missing.accdb");

        assertThatThrownBy(() -> ServerConfig.from(properties, baseDirectory))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("database.path");
    }

    private static Properties validProperties() {
        Properties properties = new Properties();
        properties.setProperty("server.port", "8888");
        properties.setProperty("server.maxConnections", "100");
        properties.setProperty("server.workerThreads", "8");
        properties.setProperty("database.path", "data/vCampus.accdb");
        properties.setProperty("session.timeoutMinutes", "30");
        properties.setProperty("inventory.reservationMinutes", "15");
        properties.setProperty("dedup.retentionHours", "24");
        return properties;
    }
}
