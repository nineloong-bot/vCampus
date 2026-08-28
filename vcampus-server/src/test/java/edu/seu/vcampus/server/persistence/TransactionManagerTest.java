package edu.seu.vcampus.server.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionManagerTest {
    private ConnectionProvider provider;
    private TransactionManager manager;

    @BeforeEach
    void createAccessDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        provider = () -> DriverManager.getConnection(url);
        manager = new TransactionManager(provider);
        try (var connection = provider.open(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblMarker (marker VARCHAR(64))");
        }
    }

    @Test
    void rollsBackWhenWorkThrows() throws Exception {
        assertThatThrownBy(() -> manager.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tblMarker (marker) VALUES (?)")) {
                statement.setString(1, "before-error");
                statement.executeUpdate();
            }
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        try (var connection = provider.open();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM tblMarker")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }
}
