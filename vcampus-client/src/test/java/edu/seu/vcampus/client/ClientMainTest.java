package edu.seu.vcampus.client;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ClientMainTest {
    @Test
    void connectionFailureDoesNotAbortClientStartup() throws Exception {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }
        ClientConnection connection = new ClientConnection("127.0.0.1", unavailablePort);

        assertThatCode(() -> ClientMain.connectForStartup(connection, Duration.ofSeconds(1)))
                .doesNotThrowAnyException();
        assertThat(connection.state()).isEqualTo(ConnectionState.FAILED);

        connection.close();
    }
}
