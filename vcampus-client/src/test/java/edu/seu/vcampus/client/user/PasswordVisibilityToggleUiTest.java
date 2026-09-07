package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.ChangePasswordDialog;
import edu.seu.vcampus.client.user.ui.InitialPasswordChangeDialog;
import edu.seu.vcampus.client.user.ui.LoginFrame;
import edu.seu.vcampus.client.user.ui.TeacherAccountApplicationDialog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PasswordVisibilityToggleUiTest {
    private static final char BULLET = '\u2022';

    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows())
                .forEach(Window::dispose));
    }

    @Test
    void everyUserPasswordFieldStartsHiddenWithAnEyeToggle() throws Exception {
        UserClientService users = mock(UserClientService.class);
        WindowSet windows = windows(users);

        assertHiddenFields(windows.login(), List.of("login.password"));
        assertHiddenFields(windows.initialChange(), List.of(
                "password.old", "password.new", "password.confirm"));
        assertHiddenFields(windows.change(), List.of(
                "change.old", "change.new", "change.confirm"));
        assertHiddenFields(windows.teacherApplication(), List.of(
                "teacher.password", "teacher.confirm"));
    }

    @Test
    void toggleChangesOnlyVisibilityAndKeepsEachPasswordFieldIndependent() throws Exception {
        UserClientService users = mock(UserClientService.class);
        ChangePasswordDialog dialog = windows(users).change();
        JPasswordField oldPassword = component(dialog, "change.old", JPasswordField.class);
        JPasswordField newPassword = component(dialog, "change.new", JPasswordField.class);
        JPasswordField confirmation = component(
                dialog, "change.confirm", JPasswordField.class);
        AbstractButton toggle = component(
                dialog, "change.old.visibility", AbstractButton.class);

        Icon hiddenIcon = toggle.getIcon();
        SwingUtilities.invokeAndWait(() -> {
            oldPassword.setText("OldPassword7");
            newPassword.setText("NewPassword8");
            confirmation.setText("NewPassword8");
            toggle.doClick();
        });

        assertThat(oldPassword.getEchoChar()).isEqualTo((char) 0);
        assertThat(oldPassword.getPassword()).containsExactly(
                'O', 'l', 'd', 'P', 'a', 's', 's', 'w', 'o', 'r', 'd', '7');
        assertThat(newPassword.getEchoChar()).isEqualTo(BULLET);
        assertThat(confirmation.getEchoChar()).isEqualTo(BULLET);
        assertThat(toggle.getAccessibleContext().getAccessibleName()).isEqualTo("隐藏密码");
        assertThat(toggle.getToolTipText()).isEqualTo("隐藏密码");
        assertThat(toggle.getIcon()).isNotNull().isNotSameAs(hiddenIcon);

        SwingUtilities.invokeAndWait(toggle::doClick);

        assertThat(oldPassword.getEchoChar()).isEqualTo(BULLET);
        assertThat(oldPassword.getPassword()).containsExactly(
                'O', 'l', 'd', 'P', 'a', 's', 's', 'w', 'o', 'r', 'd', '7');
        assertThat(toggle.getAccessibleContext().getAccessibleName()).isEqualTo("显示密码");
        assertThat(toggle.getToolTipText()).isEqualTo("显示密码");
        assertThat(toggle.getIcon()).isSameAs(hiddenIcon);
    }

    @Test
    void visibilityToggleKeepsThePasswordBorderAndErrorMessageUnchanged() throws Exception {
        LoginFrame frame = windows(mock(UserClientService.class)).login();
        JPasswordField field = component(frame, "login.password", JPasswordField.class);
        AbstractButton toggle = component(frame, "login.password.visibility", AbstractButton.class);
        JComponent inputArea = (JComponent) field.getParent();
        Border initialBorder = inputArea.getBorder();

        SwingUtilities.invokeAndWait(() -> {
            field.setText("Password7");
            toggle.doClick();
            notifyFocusGained(field);
        });

        assertThat(inputArea.getBorder()).isSameAs(initialBorder);
        assertThat(component(frame, "login.error", JLabel.class).getText()).isEqualTo(" ");
        assertThat(field.getPassword()).containsExactly(
                'P', 'a', 's', 's', 'w', 'o', 'r', 'd', '7');

        SwingUtilities.invokeAndWait(() -> {
            toggle.doClick();
            notifyFocusGained(field);
        });

        assertThat(inputArea.getBorder()).isSameAs(initialBorder);
        assertThat(component(frame, "login.error", JLabel.class).getText()).isEqualTo(" ");
    }

    private static WindowSet windows(UserClientService users) throws Exception {
        WindowSet[] result = new WindowSet[1];
        SwingUtilities.invokeAndWait(() -> result[0] = new WindowSet(
                new LoginFrame(users, ignored -> { }),
                new InitialPasswordChangeDialog(null, users, () -> { }, () -> { }),
                new ChangePasswordDialog(null, users, () -> { }),
                new TeacherAccountApplicationDialog(null, users, () -> { })));
        return result[0];
    }

    private static void assertHiddenFields(Container root, List<String> names) {
        for (String name : names) {
            JPasswordField field = component(root, name, JPasswordField.class);
            AbstractButton toggle = component(root, name + ".visibility", AbstractButton.class);
            assertThat(field.getEchoChar()).as(name).isEqualTo(BULLET);
            assertThat(field.getForeground()).as(name).isNotNull();
            assertThat(toggle.getIcon()).as(name).isNotNull();
            assertThat(toggle.getText()).as(name).isNullOrEmpty();
            assertThat(toggle.getAccessibleContext().getAccessibleName())
                    .as(name).isEqualTo("显示密码");
            assertThat(toggle.getToolTipText()).as(name).isEqualTo("显示密码");
        }
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
                    // Continue searching sibling containers.
                }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }

    private static void notifyFocusGained(Component component) {
        FocusEvent event = new FocusEvent(component, FocusEvent.FOCUS_GAINED);
        for (FocusListener listener : component.getFocusListeners()) {
            listener.focusGained(event);
        }
    }

    private record WindowSet(
            LoginFrame login,
            InitialPasswordChangeDialog initialChange,
            ChangePasswordDialog change,
            TeacherAccountApplicationDialog teacherApplication) {
    }
}
