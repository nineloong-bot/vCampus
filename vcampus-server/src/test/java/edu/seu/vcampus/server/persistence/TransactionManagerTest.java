package edu.seu.vcampus.server.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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

    @Test
    void serializesTransactionsForTheSingleFileDatabase() throws Exception {
        Connection connection = mock(Connection.class);
        TransactionManager serialized = new TransactionManager(() -> connection);
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondInside = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> serialized.inTransaction(ignored -> {
                firstInside.countDown();
                releaseFirst.await();
                return null;
            }));
            assertThat(firstInside.await(2, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> serialized.inTransaction(ignored -> {
                secondInside.countDown();
                return null;
            }));

            assertThat(secondInside.await(200, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirst.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertThat(secondInside.getCount()).isZero();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }
}
