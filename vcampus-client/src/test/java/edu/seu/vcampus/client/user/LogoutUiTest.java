package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.LoginFrame;
import edu.seu.vcampus.client.user.ui.UserUiCoordinator;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogoutUiTest {
    private static final Set<String> ADMIN_PERMISSIONS = Set.of(
            "USER_READ_ALL", "USER_ROLE_WRITE", "USER_STATUS_WRITE", "USER_AUDIT_READ");

    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows())
                .forEach(Window::dispose));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "server-failure", "disconnected"})
    void logoutAlwaysReturnsToOneSafeLoginWindow(String outcome) throws Exception {
        CompletableFuture<Void> logoutResult = new CompletableFuture<>();
        AtomicBoolean logoutCalledOnEdt = new AtomicBoolean();
        AtomicBoolean sessionClearedOnEdt = new AtomicBoolean();
        UserClientService users = mock(UserClientService.class);
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        doReturn(CompletableFuture.completedFuture(loginResult()))
                .when(users).login(anyString(), any(char[].class));
        doReturn(CompletableFuture.completedFuture(loginResult().user()))
                .when(users).getCurrentUser();
        stubAdministratorPages(users);
        doAnswer(invocation -> {
            logoutCalledOnEdt.set(SwingUtilities.isEventDispatchThread());
            return logoutResult;
        }).when(users).logout();
        doAnswer(invocation -> {
            sessionClearedOnEdt.set(SwingUtilities.isEventDispatchThread());
            return null;
        }).when(users).clearSession();

        UserUiCoordinator coordinator = new UserUiCoordinator(users, connection);
        SwingUtilities.invokeAndWait(coordinator::start);
        submitLogin(showing(LoginFrame.class));
        flushEdt();
        flushEdt();
        MainFrame main = showing(MainFrame.class);
        SwingUtilities.invokeAndWait(() -> component(
                main, "navigation.account", AbstractButton.class).doClick());
        AbstractButton detail = component(main, "account.detail", AbstractButton.class);
        JButton logout = component(main, "account.logout", JButton.class);

        assertThat(logout.getParent().getComponent(
                logout.getParent().getComponentCount() - 1)).isSameAs(logout);
        JDialog confirmation = openConfirmation(logout);
        assertThat(confirmation.getTitle()).isEqualTo("确认退出登录");
        assertThat(text(confirmation)).contains("确定要退出当前登录吗？", "取消", "退出登录");
        verify(users, never()).logout();
        SwingUtilities.invokeAndWait(() -> component(
                confirmation, "logout.confirm", AbstractButton.class).doClick());
        SwingUtilities.invokeAndWait(logout::doClick);

        assertThat(logoutCalledOnEdt).isTrue();
        assertThat(logout.isEnabled()).isFalse();
        assertThat(detail.isSelected()).isTrue();
        assertThat(logout.isSelected()).isFalse();
        verify(users, times(1)).logout();
        assertThat(showingFrames(LoginFrame.class)).isEmpty();

        if ("disconnected".equals(outcome)) {
            SwingUtilities.invokeAndWait(main::dispose);
        }
        if ("success".equals(outcome)) {
            logoutResult.complete(null);
        } else {
            logoutResult.completeExceptionally(new IllegalStateException(outcome));
        }
        flushEdt();
        flushEdt();

        assertThat(sessionClearedOnEdt).isTrue();
        verify(users, times(1)).clearSession();
        assertThat(showingFrames(MainFrame.class)).isEmpty();
        assertThat(showingFrames(LoginFrame.class)).hasSize(1);
        LoginFrame login = showing(LoginFrame.class);
        assertThat(text(login)).contains("已退出登录")
                .doesNotContain("IllegalStateException", "server-failure", "session-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "close", "escape"})
    void cancellingLogoutConfirmationKeepsCurrentMainWindow(String action) throws Exception {
        UserClientService users = mock(UserClientService.class);
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        doReturn(CompletableFuture.completedFuture(loginResult()))
                .when(users).login(anyString(), any(char[].class));
        doReturn(CompletableFuture.completedFuture(loginResult().user()))
                .when(users).getCurrentUser();
        stubAdministratorPages(users);
        UserUiCoordinator coordinator = new UserUiCoordinator(users, connection);
        SwingUtilities.invokeAndWait(coordinator::start);
        submitLogin(showing(LoginFrame.class));
        flushEdt();
        flushEdt();
        MainFrame main = showing(MainFrame.class);
        SwingUtilities.invokeAndWait(() -> component(
                main, "navigation.account", AbstractButton.class).doClick());
        AbstractButton detail = component(main, "account.detail", AbstractButton.class);
        JButton logout = component(main, "account.logout", JButton.class);

        JDialog confirmation = openConfirmation(logout);
        JButton cancel = component(confirmation, "logout.cancel", JButton.class);
        assertThat(confirmation.getRootPane().getDefaultButton()).isSameAs(cancel);
        verify(users, never()).logout();
        SwingUtilities.invokeAndWait(() -> cancel(confirmation, cancel, action));
        flushEdt();

        verify(users, never()).logout();
        assertThat(confirmation.isDisplayable()).isFalse();
        assertThat(main.isShowing()).isTrue();
        assertThat(showingFrames(LoginFrame.class)).isEmpty();
        assertThat(detail.isSelected()).isTrue();
        assertThat(logout.isEnabled()).isTrue();
    }

    @Test
    void logoutButtonRemainsLastWhenAdministratorActionsAreVisible() throws Exception {
        UserClientService users = mock(UserClientService.class);
        doReturn(CompletableFuture.completedFuture(loginResult().user()))
                .when(users).getCurrentUser();
        stubAdministratorPages(users);
        edu.seu.vcampus.client.user.ui.AccountPanel[] panel =
                new edu.seu.vcampus.client.user.ui.AccountPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] =
                new edu.seu.vcampus.client.user.ui.AccountPanel(
                        users, loginResult().user(), ADMIN_PERMISSIONS, () -> { }));
        flushEdt();

        JButton logout = component(panel[0], "account.logout", JButton.class);
        assertThat(logout.getParent().getComponent(
                logout.getParent().getComponentCount() - 1)).isSameAs(logout);
    }

    private static JDialog openConfirmation(JButton logout) throws Exception {
        SwingUtilities.invokeLater(logout::doClick);
        flushEdt();
        return showing(JDialog.class);
    }

    private static void cancel(JDialog dialog, JButton cancel, String action) {
        switch (action) {
            case "cancel" -> cancel.doClick();
            case "close" -> dialog.dispatchEvent(
                    new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
            case "escape" -> {
                KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
                Object key = dialog.getRootPane().getInputMap(
                        JComponent.WHEN_IN_FOCUSED_WINDOW).get(escape);
                Action binding = dialog.getRootPane().getActionMap().get(key);
                assertThat(binding).isNotNull();
                binding.actionPerformed(new ActionEvent(dialog,
                        ActionEvent.ACTION_PERFORMED, "escape"));
            }
            default -> throw new IllegalArgumentException(action);
        }
    }

    private static void stubAdministratorPages(UserClientService users) {
        PageResult<?> empty = new PageResult<>(List.of(), 0, 20, 0);
        doReturn(CompletableFuture.completedFuture(empty)).when(users).searchUsers(any());
        doReturn(CompletableFuture.completedFuture(empty))
                .when(users).searchSecurityAudits(any());
    }

    private static void submitLogin(LoginFrame frame) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            component(frame, "login.loginId", JTextField.class).setText("DEMO_ADMIN");
            component(frame, "login.password", JPasswordField.class).setText("AdminPassword8");
            component(frame, "login.submit", AbstractButton.class).doClick();
        });
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static <T extends Window> T showing(Class<T> type) {
        return Arrays.stream(Window.getWindows()).filter(type::isInstance)
                .map(type::cast).filter(Window::isShowing).findFirst().orElseThrow();
    }

    private static <T extends Frame> java.util.List<T> showingFrames(Class<T> type) {
        return Arrays.stream(Frame.getFrames()).filter(type::isInstance)
                .map(type::cast).filter(Frame::isShowing).toList();
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

    private static LoginResult loginResult() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 12, 0);
        UserView user = new UserView("demo", "DEMO_ADMIN", ADMIN, ACTIVE,
                false, now, 0, now, now);
        return new LoginResult("session-token", user, ADMIN_PERMISSIONS, false);
    }
}
