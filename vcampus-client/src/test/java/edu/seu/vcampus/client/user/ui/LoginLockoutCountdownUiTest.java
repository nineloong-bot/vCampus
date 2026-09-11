package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.user.service.UserClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class LoginLockoutCountdownUiTest {
    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Frame.getFrames()).forEach(Frame::dispose));
    }

    @Test
    void lockedResponseStartsThirtySecondEdtCountdownAndRetainsOnlyLoginId()
            throws Exception {
        UserClientService users = lockedService();
        LoginFrame[] frame = new LoginFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new LoginFrame(users, ignored -> { });
            frame[0].setVisible(true);
            component(frame[0], "login.loginId", JTextField.class).setText("DEMO_ADMIN");
            component(frame[0], "login.password", JPasswordField.class).setText("secret");
            component(frame[0], "login.submit", AbstractButton.class).doClick();
        });
        flushEdt();

        AbstractButton submit = component(frame[0], "login.submit", AbstractButton.class);
        JLabel error = component(frame[0], "login.error", JLabel.class);
        Timer timer = timer(frame[0]);
        assertThat(submit.isEnabled()).isFalse();
        assertThat(error.getText()).isEqualTo("登录失败次数过多，请 30 秒后再试");
        assertThat(component(frame[0], "login.loginId", JTextField.class).getText())
                .isEqualTo("DEMO_ADMIN");
        assertThat(component(frame[0], "login.password", JPasswordField.class).getPassword())
                .isEmpty();
        assertThat(timer.isRunning()).isTrue();

        tick(timer, 1);
        assertThat(error.getText()).isEqualTo("登录失败次数过多，请 29 秒后再试");
        tick(timer, 29);
        assertThat(timer.isRunning()).isFalse();
        assertThat(submit.isEnabled()).isTrue();
        assertThat(error.getText()).isBlank();
    }

    @Test
    void disposingLoginFrameStopsLockoutTimerWithoutBlockingEdt() throws Exception {
        LoginFrame[] frame = new LoginFrame[1];
        long started = System.nanoTime();
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new LoginFrame(lockedService(), ignored -> { });
            frame[0].setVisible(true);
            component(frame[0], "login.submit", AbstractButton.class).doClick();
        });
        flushEdt();
        Timer timer = timer(frame[0]);
        SwingUtilities.invokeAndWait(frame[0]::dispose);

        assertThat(timer.isRunning()).isFalse();
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(1_000);
    }

    @Test
    void loginDisplaysAllCourseDemoAccountsWithoutPrefillingCredentials() throws Exception {
        LoginFrame[] frame = new LoginFrame[1];
        SwingUtilities.invokeAndWait(() -> frame[0] = new LoginFrame(
                mock(UserClientService.class), ignored -> { }));

        assertThat(visibleText(frame[0])).contains(
                "演示账号", "管理员：DEMO_ADMIN / admin123456",
                "教师：DEMO_TEACHER / Teacher123456",
                "学生：213242478 / 12345678");
        assertThat(component(frame[0], "login.loginId", JTextField.class).getText()).isEmpty();
        assertThat(component(frame[0], "login.password", JPasswordField.class).getPassword())
                .isEmpty();
    }

    private static UserClientService lockedService() {
        UserClientService users = mock(UserClientService.class);
        doReturn(CompletableFuture.failedFuture(
                new IllegalArgumentException("AUTH_ACCOUNT_LOCKED")))
                .when(users).login(anyString(), any(char[].class));
        return users;
    }

    private static Timer timer(LoginFrame frame) throws Exception {
        for (Field field : LoginFrame.class.getDeclaredFields()) {
            if (field.getType() == Timer.class) {
                field.setAccessible(true);
                return (Timer) field.get(frame);
            }
        }
        throw new AssertionError("LoginFrame has no lockout Swing Timer");
    }

    private static void tick(Timer timer, int count) throws Exception {
        for (int index = 0; index < count; index++) {
            SwingUtilities.invokeAndWait(() -> Arrays.stream(timer.getActionListeners())
                    .forEach(listener -> listener.actionPerformed(
                            new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, "tick"))));
        }
    }

    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }

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

    private static String visibleText(Container root) {
        StringBuilder result = new StringBuilder();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) result.append(label.getText()).append(' ');
            if (child instanceof Container nested) result.append(visibleText(nested));
        }
        return result.toString();
    }
}
