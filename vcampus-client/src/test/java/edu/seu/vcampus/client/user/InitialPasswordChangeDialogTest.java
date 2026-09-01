package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.InitialPasswordChangeDialog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InitialPasswordChangeDialogTest {
    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows())
                .forEach(Window::dispose));
    }

    @Test
    void mismatchedPasswordsStayLocalAndShowAnActionableMessage() throws Exception {
        UserClientService users = mock(UserClientService.class);
        InitialPasswordChangeDialog dialog = dialog(users);

        submit(dialog, "OldPassword7", "NewPassword8", "DifferentPassword9");

        verify(users, never()).changePassword(any(char[].class), any(char[].class));
        assertThat(error(dialog)).isEqualTo("两次输入的新密码不一致");
        assertPasswordsCleared(dialog);
        assertThat(button(dialog).isEnabled()).isTrue();
    }

    @Test
    void policyFailureUsesTheSharedActionableMessage() throws Exception {
        UserClientService users = failedService("AUTH_PASSWORD_POLICY_VIOLATION");
        InitialPasswordChangeDialog dialog = dialog(users);

        submit(dialog, "OldPassword7", "weak", "weak");
        flushEdt();

        assertThat(error(dialog)).isEqualTo("密码需为 8–64 位，并同时包含字母和数字");
        assertPasswordsCleared(dialog);
        assertThat(button(dialog).isEnabled()).isTrue();
    }

    @Test
    void invalidCurrentPasswordUsesTheSharedActionableMessage() throws Exception {
        UserClientService users = failedService("AUTH_INVALID_CREDENTIALS");
        InitialPasswordChangeDialog dialog = dialog(users);

        submit(dialog, "WrongPassword7", "NewPassword8", "NewPassword8");
        flushEdt();

        assertThat(error(dialog)).isEqualTo("当前密码不正确");
        assertThat(dialog.isDisplayable()).isTrue();
    }

    @Test
    void networkAndUnknownFailuresRemainSafeAndKeepTheDialogOpen() throws Exception {
        assertSafeFailure(new ConnectException("private endpoint"), "无法连接服务器，请稍后重试");
        assertSafeFailure(new IllegalStateException("SecretHash stack token"),
                "密码修改失败，请稍后重试");
    }

    private static void assertSafeFailure(Throwable failure, String expected) throws Exception {
        UserClientService users = mock(UserClientService.class);
        doReturn(CompletableFuture.failedFuture(failure))
                .when(users).changePassword(any(char[].class), any(char[].class));
        InitialPasswordChangeDialog dialog = dialog(users);
        submit(dialog, "OldPassword7", "NewPassword8", "NewPassword8");
        flushEdt();
        assertThat(error(dialog)).isEqualTo(expected)
                .doesNotContain("SecretHash", "token", "ConnectException", "IllegalStateException");
        assertThat(dialog.isDisplayable()).isTrue();
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    private static UserClientService failedService(String code) {
        UserClientService users = mock(UserClientService.class);
        doReturn(CompletableFuture.failedFuture(new IllegalArgumentException(code)))
                .when(users).changePassword(any(char[].class), any(char[].class));
        return users;
    }

    private static InitialPasswordChangeDialog dialog(UserClientService users) throws Exception {
        InitialPasswordChangeDialog[] result = new InitialPasswordChangeDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            result[0] = new InitialPasswordChangeDialog(null, users, () -> { }, () -> { });
            result[0].addNotify();
        });
        return result[0];
    }

    private static void submit(InitialPasswordChangeDialog dialog, String oldValue,
                               String newValue, String confirmation) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            field(dialog, "password.old").setText(oldValue);
            field(dialog, "password.new").setText(newValue);
            field(dialog, "password.confirm").setText(confirmation);
            button(dialog).doClick();
        });
    }

    private static void assertPasswordsCleared(InitialPasswordChangeDialog dialog) {
        assertThat(field(dialog, "password.old").getPassword()).isEmpty();
        assertThat(field(dialog, "password.new").getPassword()).isEmpty();
        assertThat(field(dialog, "password.confirm").getPassword()).isEmpty();
    }

    private static JPasswordField field(Container root, String name) {
        return component(root, name, JPasswordField.class);
    }

    private static AbstractButton button(Container root) {
        return component(root, "password.submit", AbstractButton.class);
    }

    private static String error(Container root) {
        return component(root, "password.error", JLabel.class).getText();
    }

    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                try {
                    return component(nested, name, type);
                } catch (IllegalArgumentException ignored) {
                    // Continue searching sibling containers.
                }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
