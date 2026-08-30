package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.config.ServerConfig;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.session.SessionRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ServerMainSessionConfigurationTest {
    @Test
    void userRuntimeUsesTheConfiguredNonDefaultSessionTimeout() throws Exception {
        ServerConfig config = new ServerConfig(8888, 10, 2,
                Path.of("target", "unused.accdb").toAbsolutePath(), 7, 15, 24);

        Method factory = ServerMain.class.getDeclaredMethod("createUserRuntime", ServerConfig.class);
        factory.setAccessible(true);
        Object runtime = factory.invoke(null, config);
        Method authorizationAccessor = runtime.getClass().getDeclaredMethod("authorization");
        authorizationAccessor.setAccessible(true);
        AuthorizationService authorization =
                (AuthorizationService) authorizationAccessor.invoke(runtime);

        Field sessionsField = AuthorizationService.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        SessionRegistry sessions = (SessionRegistry) sessionsField.get(authorization);
        Field timeoutField = SessionRegistry.class.getDeclaredField("idleTimeout");
        timeoutField.setAccessible(true);

        assertThat(timeoutField.get(sessions)).isEqualTo(Duration.ofMinutes(7));
    }
}
