package edu.seu.vcampus.server.shop.demo;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ShopAuthDemoServerMainTest {
    @Test
    void defaultsToTheUnifiedApplicationPort() {
        assertThat(ShopAuthDemoServerMain.defaultPort()).isEqualTo(8888);
    }

    @Test
    void startupBannerDocumentsTheFinalFourRoleDemo() {
        assertThat(ShopAuthDemoServerMain.startupBanner(Path.of("demo.accdb"), 8888))
                .containsExactly(
                        "vCampus Shop final four-role demo server started",
                        "Database: " + Path.of("demo.accdb").toAbsolutePath(),
                        "Port: 8888",
                        "Demo logins: DEMO_BUYER, DEMO_OTHER_BUYER, DEMO_TEACHER, DEMO_ADMIN",
                        "Demo password: 123456");
    }
}
