package edu.seu.vcampus.server.routing;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RequestDeduplicatorTest {
    private RequestDeduplicator deduplicator;

    @BeforeEach
    void createAccessDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        ConnectionProvider provider = () -> DriverManager.getConnection(url);
        try (var connection = provider.open(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblRequestDedup ("
                    + "requestId VARCHAR(36) PRIMARY KEY, userId VARCHAR(36), "
                    + "clientInstanceId VARCHAR(36) NOT NULL, command VARCHAR(64) NOT NULL, "
                    + "processingStatus VARCHAR(16) NOT NULL, resultCode VARCHAR(64), "
                    + "responseSnapshot MEMO, createdAt DATETIME NOT NULL, completedAt DATETIME)");
        }
        deduplicator = new RequestDeduplicator(new TransactionManager(provider));
    }

    @Test
    void deduplicatesPreLoginRequestsByRequestIdOnly() {
        var calls = new AtomicInteger();
        var success = ResponseBody.success(EmptyResponse.INSTANCE);
        Message first = request("8e7c1a21-9d44-4c82-978b-df34326a0341");
        Message replayFromAnotherClient = request(first.requestId());

        var firstResult = deduplicator.executeOnce(first, null, "client-a", () -> {
            calls.incrementAndGet();
            return success;
        });
        var replayResult = deduplicator.executeOnce(
                replayFromAnotherClient, null, "client-b", () -> {
                    calls.incrementAndGet();
                    return success;
                });

        assertThat(firstResult).isEqualTo(success);
        assertThat(replayResult).isEqualTo(success);
        assertThat(calls).hasValue(1);
    }

    private static Message request(String requestId) {
        return new Message(requestId, MessageType.REQUEST, "USER_LOGIN", null,
                EmptyRequest.INSTANCE, System.currentTimeMillis());
    }
}
