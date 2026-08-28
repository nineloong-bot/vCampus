package edu.seu.vcampus.server.routing;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageRouterTest {
    @Test
    void routesRegisteredCommandAndRejectsUnknownCommand() {
        MessageHandler handler = (message, context) -> ResponseBody.success("ok");
        MessageRouter router = new MessageRouter(Map.of("PING", handler));

        assertThat(router.route(request("PING"), context()).data()).isEqualTo("ok");
        assertThatThrownBy(() -> router.route(request("MISSING"), context()))
                .isInstanceOf(CommandNotFoundException.class)
                .hasMessageContaining("MISSING");
    }

    private static Message request(String command) {
        return new Message("request-1", MessageType.REQUEST, command,
                null, EmptyRequest.INSTANCE, 1L);
    }

    private static ClientContext context() {
        return new ClientContext("connection-1", "127.0.0.1");
    }
}
