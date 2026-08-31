package edu.seu.vcampus.server.shop.demo;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ShopAuthDemoServerMainTest {
    @Test
    void startupBannerDocumentsTheFinalFourRoleDemo() {
        assertThat(ShopAuthDemoServerMain.startupBanner(Path.of("demo.accdb"), 19090))
                .containsExactly(
                        "vCampus Shop final four-role demo server started",
                        "Database: " + Path.of("demo.accdb").toAbsolutePath(),
                        "Port: 19090",
                        "Demo logins: DEMO_BUYER, DEMO_OTHER_BUYER, DEMO_TEACHER, DEMO_ADMIN",
                        "Demo password: 123456");
    }
}
