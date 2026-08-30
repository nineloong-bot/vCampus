package edu.seu.vcampus.client.shop.demo;

import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.common.shop.PaidOrderHistory;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.GraphicsEnvironment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.component;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.flushEdt;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopAuthDemoClientMainTest {
    @Test
    void authenticatedCompositionPassesTheLoginUserIntoTheMyPage() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        LocalDateTime now = LocalDateTime.of(2026, 8, 30, 0, 0);
        UserView user = new UserView("buyer-main", "DEMO_BUYER", UserRole.STUDENT,
                AccountStatus.ACTIVE, false, now, 0, now, now);
        MainFrame main = onEdt(() -> ShopAuthDemoClientMain.authenticatedMain(user));
        ShopClientPort shop = mock(ShopClientPort.class);
        when(shop.getPaidOrders()).thenReturn(
                CompletableFuture.completedFuture(new PaidOrderHistory(List.of())));

        onEdt(() -> ShopAuthDemoClientMain.installAuthenticatedShop(
                main, user, shop, () -> { }));
        onEdt(() -> component(main.content(), "shop.my", JButton.class).doClick());
        flushEdt();

        assertThat(component(main.content(), "my.user-id", JLabel.class).getText())
                .isEqualTo("buyer-main");
        assertThat(component(main.content(), "my.login-id", JLabel.class).getText())
                .isEqualTo("DEMO_BUYER");
        onEdt(main::dispose);
    }

    @Test
    void defaultsToTheLocalDemoServerWithoutArguments() {
        ShopAuthDemoClientMain.ServerAddress address =
                ShopAuthDemoClientMain.serverAddress(new String[0]);

        assertThat(address.host()).isEqualTo("127.0.0.1");
        assertThat(address.port()).isEqualTo(19090);
    }

    @Test
    void oneArgumentOverridesOnlyTheServerHost() {
        ShopAuthDemoClientMain.ServerAddress address =
                ShopAuthDemoClientMain.serverAddress(new String[] {"100.64.12.34"});

        assertThat(address.host()).isEqualTo("100.64.12.34");
        assertThat(address.port()).isEqualTo(19090);
    }

    @Test
    void twoArgumentsOverrideTheServerHostAndPort() {
        ShopAuthDemoClientMain.ServerAddress address =
                ShopAuthDemoClientMain.serverAddress(new String[] {"demo-tailnet", "23456"});

        assertThat(address.host()).isEqualTo("demo-tailnet");
        assertThat(address.port()).isEqualTo(23456);
    }

    @Test
    void rejectsMoreThanTwoArguments() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ShopAuthDemoClientMain.serverAddress(
                        new String[] {"127.0.0.1", "19090", "unexpected"}))
                .withMessageContaining("最多接受 2 个参数");
    }

    @Test
    void rejectsBlankServerHost() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ShopAuthDemoClientMain.serverAddress(
                        new String[] {"   "}))
                .withMessageContaining("服务器地址不能为空");
    }

    @Test
    void rejectsNonNumericServerPort() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ShopAuthDemoClientMain.serverAddress(
                        new String[] {"127.0.0.1", "not-a-port"}))
                .withMessageContaining("服务器端口必须是数字");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "65536"})
    void rejectsServerPortOutsideTheTcpRange(String port) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ShopAuthDemoClientMain.serverAddress(
                        new String[] {"127.0.0.1", port}))
                .withMessageContaining("服务器端口必须在 1..65535 范围内");
    }
}
