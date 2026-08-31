package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.LoginFrame;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoginDemoUiTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @AfterEach
    void disposeWindowsOnEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Frame.getFrames())
                .forEach(Frame::dispose));
    }

    @Test
    void userClientSendsLoginCommandAndStoresReturnedSessionToken() {
        ClientConnection connection = mock(ClientConnection.class);
        LoginResult expected = loginResult();
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(expected)))
                .when(connection).send(eq("USER_LOGIN"), any(LoginCommand.class), eq(TIMEOUT));
        UserClientService service = new UserClientService(
                connection, "demo-client", TIMEOUT);
        char[] submitted = "DemoPassword7".toCharArray();

        LoginResult actual = service.login("demo_admin", submitted).join();

        ArgumentCaptor<LoginCommand> command = ArgumentCaptor.forClass(LoginCommand.class);
        verify(connection).send(eq("USER_LOGIN"), command.capture(), eq(TIMEOUT));
        verify(connection).setSessionToken("demo-session-token");
        assertThat(command.getValue().loginId()).isEqualTo("demo_admin");
        assertThat(command.getValue().clientInstanceId()).isEqualTo("demo-client");
        assertThat(command.getValue().password()).containsOnly('\0');
        assertThat(submitted).containsOnly('\0');
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void loginNeverPerformsPotentiallyBlockingSocketSendOnEdt() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        CountDownLatch sendEntered = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        CountDownLatch loginReturned = new CountDownLatch(1);
        doAnswer(invocation -> {
            sendEntered.countDown();
            releaseSend.await(2, TimeUnit.SECONDS);
            return CompletableFuture.completedFuture(ResponseBody.success(loginResult()));
        }).when(connection).send(eq("USER_LOGIN"), any(LoginCommand.class), eq(TIMEOUT));
        UserClientService service = new UserClientService(connection, "demo-client", TIMEOUT);

        SwingUtilities.invokeLater(() -> {
            service.login("DEMO_ADMIN", "DemoPassword7".toCharArray());
            loginReturned.countDown();
        });

        assertThat(sendEntered.await(1, TimeUnit.SECONDS)).isTrue();
        boolean returnedBeforeSocketReleased = loginReturned.await(200, TimeUnit.MILLISECONDS);
        releaseSend.countDown();
        assertThat(returnedBeforeSocketReleased).isTrue();
    }

    @Test
    void userClientChangesPasswordClearsSecretsAndClearsRevokedSession() {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(EmptyResponse.INSTANCE)))
                .when(connection).send(eq("USER_CHANGE_PASSWORD"),
                        any(ChangePasswordCommand.class), eq(TIMEOUT));
        UserClientService service = new UserClientService(
                connection, "demo-client", TIMEOUT);
        char[] oldPassword = "InitialPassword7".toCharArray();
        char[] newPassword = "Replacement8".toCharArray();

        service.changePassword(oldPassword, newPassword).join();

        ArgumentCaptor<ChangePasswordCommand> command =
                ArgumentCaptor.forClass(ChangePasswordCommand.class);
        verify(connection).send(eq("USER_CHANGE_PASSWORD"), command.capture(), eq(TIMEOUT));
        verify(connection).setSessionToken(null);
        assertThat(oldPassword).containsOnly('\0');
        assertThat(newPassword).containsOnly('\0');
        assertThat(command.getValue().oldPassword()).containsOnly('\0');
        assertThat(command.getValue().newPassword()).containsOnly('\0');
    }

    @Test
    void loginShowsAllDemoAccountsWithoutPrefillingCredentials() throws Exception {
        UserClientService service = mock(UserClientService.class);
        LoginFrame[] login = new LoginFrame[1];

        SwingUtilities.invokeAndWait(() ->
                login[0] = new LoginFrame(service, result -> { }));

        assertThat(component(login[0], "login.demoTitle", JLabel.class).getText())
                .isEqualTo("演示账号");
        assertThat(component(login[0], "login.demoAdmin", JLabel.class).getText())
                .isEqualTo("管理员：DEMO_ADMIN / admin123456");
        assertThat(component(login[0], "login.demoTeacher", JLabel.class).getText())
                .isEqualTo("教师：DEMO_TEACHER / Teacher123456");
        assertThat(component(login[0], "login.demoStudent", JLabel.class).getText())
                .isEqualTo("学生：213242478 / 12345678");
        assertThat(component(login[0], "login.loginId", JTextField.class).getText())
                .isEmpty();
        assertThat(component(login[0], "login.password", JPasswordField.class).getPassword())
                .isEmpty();
    }

    @Test
    void successfulLoginClosesLoginAndShowsIdentityAndPlaceholders() throws Exception {
        UserClientService service = mock(UserClientService.class);
        doReturn(CompletableFuture.completedFuture(loginResult()))
                .when(service).login(anyString(), any(char[].class));
        AtomicReference<MainFrame> main = new AtomicReference<>();
        AtomicBoolean handoffOnEdt = new AtomicBoolean();
        LoginFrame[] login = new LoginFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            login[0] = new LoginFrame(service, result -> {
                handoffOnEdt.set(SwingUtilities.isEventDispatchThread());
                MainFrame frame = new MainFrame(result.user());
                main.set(frame);
                frame.setVisible(true);
            });
            login[0].setVisible(true);
        });
        assertThat(showingMainFrames()).isEmpty();

        submit(login[0], "DEMO_ADMIN", "DemoPassword7");
        flushEdt();

        ArgumentCaptor<char[]> submitted = ArgumentCaptor.forClass(char[].class);
        verify(service).login(eq("DEMO_ADMIN"), submitted.capture());
        assertThat(submitted.getValue()).containsOnly('\0');
        assertThat(login[0].isDisplayable()).isFalse();
        assertThat(main.get()).isNotNull();
        assertThat(main.get().isShowing()).isTrue();
        assertThat(handoffOnEdt).isTrue();
        String text = visibleText(main.get());
        assertThat(text).contains("DEMO_ADMIN", "管理员", "学籍档案", "课程中心",
                "图书借阅", "校园商城", "账户设置", "功能建设中");
    }

    @Test
    void failedLoginStaysUsableAndShowsOnlySafeMessage() throws Exception {
        UserClientService service = mock(UserClientService.class);
        doReturn(CompletableFuture.failedFuture(
                new IllegalArgumentException("AUTH_INVALID_CREDENTIALS")))
                .when(service).login(anyString(), any(char[].class));
        AtomicBoolean mainCreated = new AtomicBoolean();
        AtomicBoolean errorUpdatedOnEdt = new AtomicBoolean();
        LoginFrame[] login = new LoginFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            login[0] = new LoginFrame(service, result -> mainCreated.set(true));
            component(login[0], "login.error", JLabel.class)
                    .addPropertyChangeListener("text", event ->
                            errorUpdatedOnEdt.set(SwingUtilities.isEventDispatchThread()));
            login[0].setVisible(true);
        });

        submit(login[0], "DEMO_ADMIN", "SecretPassword7");
        flushEdt();

        assertThat(login[0].isShowing()).isTrue();
        assertThat(mainCreated).isFalse();
        assertThat(showingMainFrames()).isEmpty();
        assertThat(errorUpdatedOnEdt).isTrue();
        assertThat(component(login[0], "login.loginId", JTextField.class).getText())
                .isEqualTo("DEMO_ADMIN");
        assertThat(component(login[0], "login.password", JPasswordField.class).getPassword())
                .isEmpty();
        assertThat(component(login[0], "login.submit", AbstractButton.class).isEnabled())
                .isTrue();
        String error = component(login[0], "login.error", JLabel.class).getText();
        assertThat(error).contains("用户名或密码错误")
                .doesNotContain("SecretPassword7", "hash", "salt",
                        "IllegalArgumentException", "AUTH_INVALID_CREDENTIALS");
    }

    private static void submit(LoginFrame frame, String loginId, String password)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            component(frame, "login.loginId", JTextField.class).setText(loginId);
            component(frame, "login.password", JPasswordField.class).setText(password);
            component(frame, "login.submit", AbstractButton.class).doClick();
        });
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static <T extends Component> T component(
            Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container nested) {
                try {
                    return component(nested, name, type);
                } catch (IllegalArgumentException ignored) {
                    // Continue searching siblings.
                }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }

    private static String visibleText(Container root) {
        StringBuilder text = new StringBuilder();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) {
                text.append(label.getText()).append(' ');
            }
            if (child instanceof AbstractButton button) {
                text.append(button.getText()).append(' ');
            }
            if (child instanceof Container nested) {
                text.append(visibleText(nested));
            }
        }
        return text.toString();
    }

    private static Frame[] showingMainFrames() {
        return Arrays.stream(Frame.getFrames())
                .filter(MainFrame.class::isInstance).filter(Frame::isShowing)
                .toArray(Frame[]::new);
    }

    private static LoginResult loginResult() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 28, 12, 0);
        UserView user = new UserView("demo-user", "DEMO_ADMIN", ADMIN, ACTIVE,
                false, now, 0, now, now);
        return new LoginResult("demo-session-token", user, Set.of(), false);
    }
}
