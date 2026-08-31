package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionReplacementUiTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows())
                .forEach(Window::dispose));
    }

    @Test
    void explicitSessionExpiryShowsOneWarningThenReturnsToOneLoginWindow() throws Exception {
        AtomicReference<CompletableFuture<ResponseBody<UserView>>> current =
                new AtomicReference<>(CompletableFuture.completedFuture(
                        ResponseBody.success(loginResult().user())));
        Fixture fixture = fixture(current);
        MainFrame main = login(fixture.coordinator());
        awaitCurrentChecks(fixture.connection(), 1);
        clearInvocations(fixture.connection());
        current.set(CompletableFuture.completedFuture(
                ResponseBody.failure("AUTH_SESSION_EXPIRED", "会话已失效", null)));
        Timer monitor = timerInside(fixture.coordinator());

        fire(monitor);
        JDialog warning = awaitShowing(JDialog.class);

        assertThat(warning.getTitle()).isEqualTo("登录安全提醒");
        assertThat(text(warning)).contains(
                "该账号已在其他位置登录，可能存在密码泄露。请及时修改密码。")
                .doesNotContain("AUTH_SESSION_EXPIRED", "session-token", "Exception");
        assertThat(main.isEnabled()).isFalse();
        assertThat(showing(JDialog.class)).hasSize(1);
        assertThat(monitor.isRunning()).isFalse();

        fire(timerInside(warning));
        awaitNoShowing(MainFrame.class);

        verify(fixture.connection()).setSessionToken(null);
        assertThat(showing(LoginFrame.class)).hasSize(1);
        assertThat(text(showing(LoginFrame.class).getFirst()))
                .contains("登录已在其他位置失效，请重新登录")
                .doesNotContain("AUTH_SESSION_EXPIRED", "session-token");
    }

    @Test
    void networkFailureKeepsSessionAndWindowAndClosingWindowStopsMonitoring() throws Exception {
        AtomicReference<CompletableFuture<ResponseBody<UserView>>> current =
                new AtomicReference<>(CompletableFuture.completedFuture(
                        ResponseBody.success(loginResult().user())));
        Fixture fixture = fixture(current);
        MainFrame main = login(fixture.coordinator());
        awaitCurrentChecks(fixture.connection(), 1);
        clearInvocations(fixture.connection());
        current.set(CompletableFuture.failedFuture(
                new IOException("temporary network failure")));
        Timer monitor = timerInside(fixture.coordinator());

        fire(monitor);
        awaitCurrentChecks(fixture.connection(), 1);

        assertThat(main.isShowing()).isTrue();
        assertThat(main.isEnabled()).isTrue();
        assertThat(showing(JDialog.class)).isEmpty();
        assertThat(showing(LoginFrame.class)).isEmpty();
        assertThat(monitor.isRunning()).isTrue();

        SwingUtilities.invokeAndWait(main::dispose);
        flushEdt();
        assertThat(monitor.isRunning()).isFalse();
    }

    @Test
    void sessionChecksNeverOverlapAndContinueAfterAnOrdinarySuccess() throws Exception {
        CompletableFuture<ResponseBody<UserView>> pending = new CompletableFuture<>();
        AtomicReference<CompletableFuture<ResponseBody<UserView>>> current =
                new AtomicReference<>(CompletableFuture.completedFuture(
                        ResponseBody.success(loginResult().user())));
        Fixture fixture = fixture(current);
        login(fixture.coordinator());
        awaitCurrentChecks(fixture.connection(), 1);
        clearInvocations(fixture.connection());
        current.set(pending);
        Timer monitor = timerInside(fixture.coordinator());

        fire(monitor);
        fire(monitor);
        awaitCurrentChecks(fixture.connection(), 1);
        verify(fixture.connection(), times(1)).send(
                org.mockito.ArgumentMatchers.eq("USER_GET_CURRENT"), any(),
                org.mockito.ArgumentMatchers.eq(TIMEOUT));

        pending.complete(ResponseBody.success(loginResult().user()));
        flushEdt();
        current.set(CompletableFuture.completedFuture(ResponseBody.success(loginResult().user())));
        fire(monitor);
        awaitCurrentChecks(fixture.connection(), 2);
    }

    @Test
    void successfulPasswordChangeStopsMonitoringBeforeReturningToLogin() throws Exception {
        AtomicReference<CompletableFuture<ResponseBody<UserView>>> current =
                new AtomicReference<>(CompletableFuture.completedFuture(
                        ResponseBody.success(loginResult().user())));
        Fixture fixture = fixture(current);
        MainFrame main = login(fixture.coordinator());
        Timer monitor = timerInside(fixture.coordinator());
        SwingUtilities.invokeAndWait(() -> component(
                main, "navigation.account", AbstractButton.class).doClick());
        SwingUtilities.invokeLater(() -> component(
                main, "account.password", AbstractButton.class).doClick());
        JDialog dialog = awaitShowing(JDialog.class);

        SwingUtilities.invokeAndWait(() -> {
            component(dialog, "change.old", JPasswordField.class).setText("Password1");
            component(dialog, "change.new", JPasswordField.class).setText("Replacement8");
            component(dialog, "change.confirm", JPasswordField.class).setText("Replacement8");
            component(dialog, "change.submit", AbstractButton.class).doClick();
        });
        flushEdt();

        assertThat(monitor.isRunning()).isFalse();
        awaitNoShowing(MainFrame.class);
        assertThat(showing(LoginFrame.class)).hasSize(1);
    }

    @Test
    void passwordChangeRejectedByAdministratorResetReturnsToLoginWithoutFreezing()
            throws Exception {
        AtomicReference<CompletableFuture<ResponseBody<UserView>>> current =
                new AtomicReference<>(CompletableFuture.completedFuture(
                        ResponseBody.success(loginResult().user())));
        CompletableFuture<ResponseBody<EmptyResponse>> expired =
                CompletableFuture.completedFuture(ResponseBody.failure(
                        "AUTH_SESSION_EXPIRED", "会话已失效", null));
        Fixture fixture = fixture(current, expired);
        MainFrame main = login(fixture.coordinator());
        SwingUtilities.invokeAndWait(() -> component(
                main, "navigation.account", AbstractButton.class).doClick());
        SwingUtilities.invokeLater(() -> component(
                main, "account.password", AbstractButton.class).doClick());
        JDialog dialog = awaitShowing(JDialog.class);

        SwingUtilities.invokeAndWait(() -> {
            component(dialog, "change.old", JPasswordField.class).setText("Password1");
            component(dialog, "change.new", JPasswordField.class).setText("Replacement8");
            component(dialog, "change.confirm", JPasswordField.class).setText("Replacement8");
            component(dialog, "change.submit", AbstractButton.class).doClick();
        });
        awaitNoShowing(MainFrame.class);

        assertThat(showing(JDialog.class)).isEmpty();
        assertThat(showing(LoginFrame.class)).hasSize(1);
        assertThat(text(showing(LoginFrame.class).getFirst()))
                .contains("登录状态已失效，请重新登录")
                .doesNotContain("AUTH_SESSION_EXPIRED", "session-token", "Exception");
        verify(fixture.connection()).setSessionToken(null);
    }

    private static Fixture fixture(
            AtomicReference<CompletableFuture<ResponseBody<UserView>>> current) {
        return fixture(current, CompletableFuture.completedFuture(
                ResponseBody.success(EmptyResponse.INSTANCE)));
    }

    private static Fixture fixture(
            AtomicReference<CompletableFuture<ResponseBody<UserView>>> current,
            CompletableFuture<ResponseBody<EmptyResponse>> passwordChange) {
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        doAnswer(invocation -> response(invocation.getArgument(0), current, passwordChange))
                .when(connection).send(anyString(), any(Serializable.class), any(Duration.class));
        UserClientService users = new UserClientService(connection, "client", TIMEOUT);
        return new Fixture(connection, new UserUiCoordinator(users, connection));
    }

    private static CompletableFuture<?> response(
            String command, AtomicReference<CompletableFuture<ResponseBody<UserView>>> current,
            CompletableFuture<ResponseBody<EmptyResponse>> passwordChange) {
        return switch (command) {
            case "USER_LOGIN" -> CompletableFuture.completedFuture(
                    ResponseBody.success(loginResult()));
            case "USER_GET_CURRENT" -> current.get();
            case "USER_CHANGE_PASSWORD" -> passwordChange;
            default -> CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unexpected command"));
        };
    }

    private static MainFrame login(UserUiCoordinator coordinator) throws Exception {
        SwingUtilities.invokeAndWait(coordinator::start);
        LoginFrame login = awaitShowing(LoginFrame.class);
        SwingUtilities.invokeAndWait(() -> {
            component(login, "login.loginId", JTextField.class).setText("DEMO_ADMIN");
            component(login, "login.password", JPasswordField.class).setText("AdminPassword8");
            component(login, "login.submit", AbstractButton.class).doClick();
        });
        return awaitShowing(MainFrame.class);
    }

    private static void awaitCurrentChecks(ClientConnection connection, int count) {
        verify(connection, org.mockito.Mockito.timeout(2_000).times(count)).send(
                org.mockito.ArgumentMatchers.eq("USER_GET_CURRENT"), any(),
                org.mockito.ArgumentMatchers.eq(TIMEOUT));
    }

    private static void fire(Timer timer) throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(timer.getActionListeners())
                .forEach(listener -> listener.actionPerformed(
                        new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, "test"))));
        flushEdt();
    }

    private static Timer timerInside(Object owner) throws Exception {
        return timerInside(owner, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static Timer timerInside(Object owner, Set<Object> visited) throws Exception {
        if (!visited.add(owner)) {
            throw new AssertionError("Already inspected UI helper");
        }
        for (Field field : owner.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(owner);
            if (value instanceof Timer timer) return timer;
            if (value != null && value.getClass().getPackageName()
                    .equals(UserUiCoordinator.class.getPackageName())) {
                try {
                    return timerInside(value, visited);
                } catch (AssertionError ignored) {
                    // Keep searching other package-private collaborators.
                }
            }
        }
        throw new AssertionError("No Swing session timer found");
    }

    private static <T extends Window> T awaitShowing(Class<T> type) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            flushEdt();
            java.util.List<T> found = showing(type);
            if (!found.isEmpty()) return found.getFirst();
            Thread.sleep(10);
        }
        throw new AssertionError("No showing window: " + type.getSimpleName());
    }

    private static void awaitNoShowing(Class<? extends Window> type) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            flushEdt();
            if (showing(type).isEmpty()) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Window remained visible: " + type.getSimpleName());
    }

    private static <T extends Window> java.util.List<T> showing(Class<T> type) {
        return Arrays.stream(Window.getWindows()).filter(type::isInstance)
                .map(type::cast).filter(Window::isShowing).toList();
    }

    private static <T extends Component> T component(
            Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                try {
                    return component(nested, name, type);
                } catch (IllegalArgumentException ignored) {
                    // Continue searching sibling component trees.
                }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }

    private static String text(Container root) {
        StringBuilder result = new StringBuilder();
        for (Component child : root.getComponents()) {
            if (child instanceof javax.swing.JLabel label) {
                result.append(label.getText()).append(' ');
            }
            if (child instanceof AbstractButton button) {
                result.append(button.getText()).append(' ');
            }
            if (child instanceof Container nested) result.append(text(nested));
        }
        return result.toString();
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static LoginResult loginResult() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 12, 0);
        UserView user = new UserView("demo", "DEMO_ADMIN", ADMIN, ACTIVE,
                false, now, 0, now, now);
        return new LoginResult("session-token", user, Set.of(), false);
    }

    private record Fixture(ClientConnection connection, UserUiCoordinator coordinator) { }
}
