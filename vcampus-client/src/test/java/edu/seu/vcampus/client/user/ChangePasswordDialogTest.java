package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.ChangePasswordDialog;
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

class ChangePasswordDialogTest {
    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows())
                .forEach(Window::dispose));
    }

    @Test
    void mismatchIsLocalAndPolicyAndCredentialFailuresAreActionable() throws Exception {
        UserClientService mismatchUsers = mock(UserClientService.class);
        ChangePasswordDialog mismatch = dialog(mismatchUsers);
        submit(mismatch, "OldPassword7", "NewPassword8", "DifferentPassword9");
        verify(mismatchUsers, never()).changePassword(any(char[].class), any(char[].class));
        assertThat(error(mismatch)).isEqualTo("两次输入的新密码不一致");
        assertPasswordsCleared(mismatch);

        assertFailure("AUTH_PASSWORD_POLICY_VIOLATION",
                "密码需为 8–64 位，并同时包含字母和数字");
        assertFailure("AUTH_INVALID_CREDENTIALS", "当前密码不正确");
    }

    @Test
    void networkAndUnknownFailuresRemainSafe() throws Exception {
        assertFailure(new ConnectException("private endpoint"), "无法连接服务器，请稍后重试");
        assertFailure(new IllegalStateException("SecretHash stack token"),
                "密码修改失败，请稍后重试");
    }

    private static void assertFailure(String code, String expected) throws Exception {
        assertFailure(new IllegalArgumentException(code), expected);
    }

    private static void assertFailure(Throwable failure, String expected) throws Exception {
        UserClientService users = mock(UserClientService.class);
        doReturn(CompletableFuture.failedFuture(failure))
                .when(users).changePassword(any(char[].class), any(char[].class));
        ChangePasswordDialog dialog = dialog(users);
        submit(dialog, "OldPassword7", "NewPassword8", "NewPassword8");
        flushEdt();
        assertThat(error(dialog)).isEqualTo(expected)
                .doesNotContain("SecretHash", "token", "ConnectException", "IllegalStateException");
        assertThat(dialog.isDisplayable()).isTrue();
        assertThat(button(dialog).isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    private static ChangePasswordDialog dialog(UserClientService users) throws Exception {
        ChangePasswordDialog[] result = new ChangePasswordDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            result[0] = new ChangePasswordDialog(null, users, () -> { });
            result[0].addNotify();
        });
        return result[0];
    }

    private static void submit(ChangePasswordDialog dialog, String oldValue,
                               String newValue, String confirmation) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            field(dialog, "change.old").setText(oldValue);
            field(dialog, "change.new").setText(newValue);
            field(dialog, "change.confirm").setText(confirmation);
            button(dialog).doClick();
        });
    }

    private static void assertPasswordsCleared(ChangePasswordDialog dialog) {
        assertThat(field(dialog, "change.old").getPassword()).isEmpty();
        assertThat(field(dialog, "change.new").getPassword()).isEmpty();
        assertThat(field(dialog, "change.confirm").getPassword()).isEmpty();
    }

    private static JPasswordField field(Container root, String name) {
        return component(root, name, JPasswordField.class);
    }

    private static AbstractButton button(Container root) {
        return component(root, "change.submit", AbstractButton.class);
    }

    private static String error(Container root) {
        return component(root, "change.error", JLabel.class).getText();
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
