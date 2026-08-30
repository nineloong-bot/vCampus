package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.LoginFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class LoginErrorMessageUiTest {
    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows())
                .forEach(Window::dispose));
    }

    @ParameterizedTest
    @MethodSource("safeFailures")
    void mapsStableFailuresWithoutLeakingCodesAndRetainsOnlyLoginId(
            Throwable failure, String expectedMessage) throws Exception {
        UserClientService service = mock(UserClientService.class);
        doReturn(CompletableFuture.failedFuture(failure))
                .when(service).login(anyString(), any(char[].class));
        LoginFrame[] login = new LoginFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            login[0] = new LoginFrame(service, result -> { });
            login[0].setVisible(true);
            component(login[0], "login.loginId", JTextField.class).setText("RETAINED_LOGIN");
            component(login[0], "login.password", JPasswordField.class)
                    .setText("SecretPassword7");
            component(login[0], "login.submit", AbstractButton.class).doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        String visible = component(login[0], "login.error", JLabel.class).getText();
        assertThat(visible).isEqualTo(expectedMessage)
                .doesNotContain("AUTH_", "COMMON_", "Exception", "SecretPassword7");
        assertThat(component(login[0], "login.loginId", JTextField.class).getText())
                .isEqualTo("RETAINED_LOGIN");
        assertThat(component(login[0], "login.password", JPasswordField.class).getPassword())
                .isEmpty();
    }

    private static Stream<Arguments> safeFailures() {
        return Stream.of(
                Arguments.of(new IllegalArgumentException("AUTH_INVALID_CREDENTIALS"),
                        "用户名或密码错误"),
                Arguments.of(new IllegalArgumentException("AUTH_ACCOUNT_PENDING"),
                        "账户正在审核，暂不能登录"),
                Arguments.of(new IllegalArgumentException("AUTH_ACCOUNT_DISABLED"),
                        "账户已停用，请联系管理员"),
                Arguments.of(new IllegalArgumentException("AUTH_ACCOUNT_LOCKED"),
                        "登录失败次数过多，请 30 秒后再试"),
                Arguments.of(new java.net.ConnectException("connection refused"),
                        "无法连接服务器，请检查服务端是否启动"),
                Arguments.of(new IllegalArgumentException("COMMON_INTERNAL_ERROR"),
                        "登录暂时不可用，请稍后再试"));
    }

    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                try { return component(nested, name, type); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        throw new IllegalArgumentException("Missing " + name);
    }
}
