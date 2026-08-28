package edu.seu.vcampus.server.network;

import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ClientConnectionTest {
    @Test
    void concurrentSendsProduceTwoIntactMessages() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ClientConnection connection = ClientConnection.forOutput(bytes);
             var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                connection.send(response("r1"));
                return null;
            });
            var second = pool.submit(() -> {
                connection.send(response("r2"));
                return null;
            });
            first.get();
            second.get();
        }

        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Message one = (Message) input.readObject();
            Message two = (Message) input.readObject();
            assertThat(Set.of(one.requestId(), two.requestId())).containsExactlyInAnyOrder("r1", "r2");
        }
    }

    private static Message response(String requestId) {
        return new Message(requestId, MessageType.RESPONSE, "TEST",
                null, EmptyResponse.INSTANCE, 1L);
    }
}
