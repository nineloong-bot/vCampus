package edu.seu.vcampus.server.user;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.bootstrap.ServerMain;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.MessageRouter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServerMainAuditAssemblyTest {
    @Test
    void registersSeparateSecurityAuditSearchWithoutChangingUserCommands() throws Exception {
        MessageRouter router = new MessageRouter(Map.of());
        MessageHandler handler = (message, context) ->
                ResponseBody.success(EmptyResponse.INSTANCE);
        Method register = ServerMain.class.getDeclaredMethod(
                "registerSecurityAudit", MessageRouter.class, MessageHandler.class);
        register.setAccessible(true);

        register.invoke(null, router, handler);

        ResponseBody<?> response = router.route(new Message("request", MessageType.REQUEST,
                "SECURITY_AUDIT_SEARCH", "token", EmptyRequest.INSTANCE, 0),
                new ClientContext("connection", "127.0.0.1"));
        assertThat(response.success()).isTrue();
    }
}
