package edu.seu.vcampus.client.core.network;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.network.SocketServer;
import edu.seu.vcampus.server.routing.MessageRouter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ClientServerIntegrationTest {
    @Test
    void connectsAndCompletesPingRoundTrip() throws Exception {
        MessageRouter router = new MessageRouter(Map.of(
                "PING", (request, context) -> ResponseBody.success(EmptyResponse.INSTANCE)));
        try (SocketServer server = new SocketServer(0, 2, 10, router);
             var serverThread = Executors.newSingleThreadExecutor();
             ClientConnection client = new ClientConnection("127.0.0.1", server.localPort())) {
            var serving = serverThread.submit(() -> {
                server.serve();
                return null;
            });

            client.connect(Duration.ofSeconds(2));
            ResponseBody<EmptyResponse> response = client
                    .<EmptyResponse>send("PING", EmptyRequest.INSTANCE, Duration.ofSeconds(2))
                    .get();

            assertThat(response.success()).isTrue();
            assertThat(response.data()).isEqualTo(EmptyResponse.INSTANCE);
            client.close();
            server.stopAccepting();
            assertThat(server.awaitRequests(Duration.ofSeconds(2))).isTrue();
            serving.get();
        }
    }
}
