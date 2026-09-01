package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.MyStudentProfilePanel;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.InitialPasswordChangeDialog;
import edu.seu.vcampus.client.user.ui.LoginFrame;
import edu.seu.vcampus.client.user.ui.UserUiCoordinator;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Window;
import java.time.LocalDateTime;
import java.time.Duration;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static edu.seu.vcampus.common.user.UserRole.STUDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialPasswordChangeUiTest {
    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows())
                .forEach(Window::dispose));
    }

    @Test
    void restrictedStudentLoginConstructsNoShellOrProfileThenReturnsForRelogin()
            throws Exception {
        UserClientService users = mock(UserClientService.class);
        ClientConnection connection = mock(ClientConnection.class);
        AtomicInteger studentRequests = new AtomicInteger();
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        doReturn(CompletableFuture.completedFuture(loginResult(STUDENT, true)),
                CompletableFuture.completedFuture(loginResult(false)))
                .when(users).login(anyString(), any(char[].class));
        doReturn(CompletableFuture.completedFuture(null))
                .when(users).changePassword(any(char[].class), any(char[].class));
        UserUiCoordinator coordinator = new UserUiCoordinator(users,
                students(studentRequests), connection);
        SwingUtilities.invokeAndWait(coordinator::start);
        List<MainFrame> framesBeforeLogin = onEdt(InitialPasswordChangeUiTest::mainFrames);

        submitLogin(showing(LoginFrame.class), "STUDENT_1", "InitialPassword7");
        flushEdt();

        assertThat(onEdt(InitialPasswordChangeUiTest::mainFrames))
                .containsExactlyElementsOf(framesBeforeLogin);
        assertThat(studentRequests).hasValue(0);
        InitialPasswordChangeDialog dialog = showing(InitialPasswordChangeDialog.class);
        assertThat(text(dialog)).contains("首次修改密码", "退出登录")
                .doesNotContain("学籍档案", "课程中心", "session-token");

        SwingUtilities.invokeAndWait(() -> {
            component(dialog, "password.old", JPasswordField.class)
                    .setText("InitialPassword7");
            component(dialog, "password.new", JPasswordField.class)
                    .setText("Replacement8");
            component(dialog, "password.confirm", JPasswordField.class)
                    .setText("Replacement8");
            component(dialog, "password.submit", AbstractButton.class).doClick();
        });
        flushEdt();

        verify(users).clearSession();
        assertThat(dialog.isDisplayable()).isFalse();
        LoginFrame returned = showing(LoginFrame.class);
        assertThat(text(returned)).contains("密码修改成功，请使用新密码重新登录");
        assertThat(showingFrames(MainFrame.class)).isEmpty();

        submitLogin(returned, "DEMO_ADMIN", "Replacement8");
        flushEdt();
        assertThat(showing(MainFrame.class).isShowing()).isTrue();
    }

    @Test
    void passwordChangeFailureKeepsDialogUsableAndClearsAllPasswords() throws Exception {
        UserClientService users = mock(UserClientService.class);
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        doReturn(CompletableFuture.completedFuture(loginResult(true)))
                .when(users).login(anyString(), any(char[].class));
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("server.SecretHash")))
                .when(users).changePassword(any(char[].class), any(char[].class));
        UserUiCoordinator coordinator = new UserUiCoordinator(users, connection);
        SwingUtilities.invokeAndWait(coordinator::start);
        submitLogin(showing(LoginFrame.class), "DEMO_ADMIN", "InitialPassword7");
        flushEdt();
        InitialPasswordChangeDialog dialog = showing(InitialPasswordChangeDialog.class);

        SwingUtilities.invokeAndWait(() -> {
            component(dialog, "password.old", JPasswordField.class).setText("WrongPassword7");
            component(dialog, "password.new", JPasswordField.class).setText("Replacement8");
            component(dialog, "password.confirm", JPasswordField.class).setText("Replacement8");
            component(dialog, "password.submit", AbstractButton.class).doClick();
        });
        flushEdt();

        assertThat(dialog.isShowing()).isTrue();
        assertThat(component(dialog, "password.old", JPasswordField.class).getPassword()).isEmpty();
        assertThat(component(dialog, "password.new", JPasswordField.class).getPassword()).isEmpty();
        assertThat(component(dialog, "password.confirm", JPasswordField.class).getPassword()).isEmpty();
        assertThat(component(dialog, "password.submit", AbstractButton.class).isEnabled()).isTrue();
        assertThat(component(dialog, "password.error", JLabel.class).getText())
                .contains("密码修改失败")
                .doesNotContain("server", "SecretHash", "IllegalStateException", "token");
        assertThat(showingFrames(MainFrame.class)).isEmpty();
    }

    @Test
    void unrestrictedStudentLoginShowsTheRealProfileWhenStudentServiceIsAvailable()
            throws Exception {
        UserClientService users = mock(UserClientService.class);
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        doReturn(CompletableFuture.completedFuture(loginResult(STUDENT, false)))
                .when(users).login(anyString(), any(char[].class));
        UserUiCoordinator coordinator = new UserUiCoordinator(users, students(), connection);
        SwingUtilities.invokeAndWait(coordinator::start);

        submitLogin(showing(LoginFrame.class), "STUDENT_1", "StudentPassword7");
        flushEdt();

        MainFrame main = showing(MainFrame.class);
        assertThat(component(main.content(), "student.profile", MyStudentProfilePanel.class)
                .isVisible()).isTrue();
    }

    private static void submitLogin(LoginFrame frame, String id, String password)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            component(frame, "login.loginId", JTextField.class).setText(id);
            component(frame, "login.password", JPasswordField.class).setText(password);
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
                .map(type::cast).filter(Frame::isShowing)
                .toList();
    }

    private static List<MainFrame> mainFrames() {
        return Arrays.stream(Frame.getFrames()).filter(MainFrame.class::isInstance)
                .map(MainFrame.class::cast).toList();
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.get();
    }

    private static <T extends Component> T component(
            Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                try { return component(nested, name, type); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }

    private static String text(Container root) {
        StringBuilder result = new StringBuilder();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) result.append(label.getText()).append(' ');
            if (child instanceof AbstractButton button) result.append(button.getText()).append(' ');
            if (child instanceof Container nested) result.append(text(nested));
        }
        return result.toString();
    }

    private static LoginResult loginResult(boolean restricted) {
        return loginResult(ADMIN, restricted);
    }

    private static LoginResult loginResult(edu.seu.vcampus.common.user.UserRole role,
                                           boolean restricted) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 29, 12, 0);
        UserView user = new UserView("demo", "DEMO_" + role, role, ACTIVE,
                restricted, now, 0, now, now);
        return new LoginResult("session-token", user, Set.of(), restricted);
    }

    private static StudentClientService students() {
        return students(new AtomicInteger());
    }

    private static StudentClientService students(AtomicInteger requests) {
        return new StudentClientService(new StudentRequestClient() {
            @Override
            public <T extends Serializable> CompletableFuture<edu.seu.vcampus.common.protocol.ResponseBody<T>>
            send(String command, Serializable body, Duration timeout) {
                requests.incrementAndGet();
                return CompletableFuture.failedFuture(new IllegalStateException("not displayed"));
            }
        }, Duration.ofSeconds(1));
    }
}
