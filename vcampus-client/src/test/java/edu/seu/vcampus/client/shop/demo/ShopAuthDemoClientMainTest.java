package edu.seu.vcampus.client.shop.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ShopAuthDemoClientMainTest {
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
