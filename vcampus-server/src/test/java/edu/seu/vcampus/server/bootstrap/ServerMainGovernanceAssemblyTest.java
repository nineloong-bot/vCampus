package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.config.ServerConfig;
import edu.seu.vcampus.server.governance.ModuleAdministrationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that production assembly exposes the dedicated-role governance service. */
class ServerMainGovernanceAssemblyTest {
    @Test
    void runtimeBuildsModuleAdministrationService() throws Exception {
        ServerConfig config = new ServerConfig(8888, 10, 2,
                Path.of("target", "unused.accdb").toAbsolutePath(), 7, 15, 24);
        Method factory = ServerMain.class.getDeclaredMethod("createRuntime", ServerConfig.class);
        factory.setAccessible(true);
        Object runtime = factory.invoke(null, config);

        Method accessor = runtime.getClass().getDeclaredMethod("governance");
        accessor.setAccessible(true);

        assertThat(accessor.invoke(runtime)).isInstanceOf(ModuleAdministrationService.class);
    }
}
