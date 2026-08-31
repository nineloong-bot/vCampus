package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.UserManagementPanel;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ResetStudentPasswordCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSummary;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StudentPasswordResetUiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 31, 10, 0);

    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows())
                .forEach(Window::dispose));
    }

    @Test
    void removesRoleEntryAndShowsResetOnlyForSelectedStudentAfterConfirmation()
            throws Exception {
        UserClientService users = mock(UserClientService.class);
        UserSummary teacher = summary("teacher", UserRole.TEACHER, 1);
        UserSummary student = summary("student", UserRole.STUDENT, 2);
        doReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(teacher, student), 0, 20, 2)))
                .when(users).searchUsers(any());
        doReturn(CompletableFuture.completedFuture(view(student)))
                .when(users).resetStudentPassword(any(ResetStudentPasswordCommand.class));
        UserManagementPanel[] panel = new UserManagementPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new UserManagementPanel(users,
                Set.of("USER_READ_ALL", "USER_ROLE_WRITE", "USER_STATUS_WRITE",
                        "USER_PASSWORD_RESET")));
        flushEdt();

        assertThat(find(panel[0], "users.role")).isNull();
        JTable table = component(panel[0], "users.table", JTable.class);
        AbstractButton reset = component(panel[0], "users.resetPassword", AbstractButton.class);
        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
        assertThat(reset.isVisible()).isFalse();
        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(1, 1));
        assertThat(reset.isVisible()).isTrue();

        JDialog first = openConfirmation(reset);
        assertThat(text(first)).contains("系统默认密码", "学生下次登录必须修改密码")
                .doesNotContain("12345678", "token", "hash", "salt");
        verify(users, never()).resetStudentPassword(any());
        SwingUtilities.invokeAndWait(() -> component(
                first, "studentReset.cancel", AbstractButton.class).doClick());
        verify(users, never()).resetStudentPassword(any());

        JDialog second = openConfirmation(reset);
        SwingUtilities.invokeAndWait(() -> component(
                second, "studentReset.confirm", AbstractButton.class).doClick());
        flushEdt();

        verify(users).resetStudentPassword(new ResetStudentPasswordCommand(
                student.userId(), student.rowVersion()));
        assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
    }

    @Test
    void resetFailureUsesSafeUiMessageAndKeepsPanelUsable() throws Exception {
        UserClientService users = mock(UserClientService.class);
        UserSummary student = summary("student", UserRole.STUDENT, 2);
        doReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(student), 0, 20, 1)))
                .when(users).searchUsers(any());
        doReturn(CompletableFuture.failedFuture(new IllegalStateException(
                "SQLException password=12345678 token=secret")))
                .when(users).resetStudentPassword(any());
        UserManagementPanel[] panel = new UserManagementPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new UserManagementPanel(users,
                Set.of("USER_READ_ALL", "USER_PASSWORD_RESET")));
        flushEdt();
        JTable table = component(panel[0], "users.table", JTable.class);
        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
        AbstractButton reset = component(panel[0], "users.resetPassword", AbstractButton.class);
        JDialog dialog = openConfirmation(reset);
        SwingUtilities.invokeAndWait(() -> component(
                dialog, "studentReset.confirm", AbstractButton.class).doClick());
        flushEdt();

        assertThat(reset.isEnabled()).isTrue();
        assertThat(text(panel[0])).contains("密码初始化失败，请稍后重试")
                .doesNotContain("SQLException", "12345678", "secret");
    }

    private static JDialog openConfirmation(AbstractButton reset) throws Exception {
        SwingUtilities.invokeLater(reset::doClick);
        flushEdt();
        return Arrays.stream(Window.getWindows()).filter(JDialog.class::isInstance)
                .map(JDialog.class::cast).filter(Window::isShowing).findFirst().orElseThrow();
    }

    private static UserSummary summary(String id, UserRole role, long version) {
        return new UserSummary(id, id.toUpperCase(), role, AccountStatus.ACTIVE, NOW, version);
    }

    private static UserView view(UserSummary summary) {
        return new UserView(summary.userId(), summary.loginId(), summary.role(),
                summary.accountStatus(), true, summary.lastLoginAt(),
                summary.rowVersion() + 1, NOW, NOW);
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static Component find(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) {
                Component found = find(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends Component> T component(
            Container root, String name, Class<T> type) {
        Component found = find(root, name);
        if (!type.isInstance(found)) throw new IllegalArgumentException("Missing " + name);
        return type.cast(found);
    }

    private static String text(Container root) {
        StringBuilder text = new StringBuilder();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) text.append(label.getText()).append(' ');
            if (child instanceof AbstractButton button) text.append(button.getText()).append(' ');
            if (child instanceof Container nested) text.append(text(nested));
        }
        return text.toString();
    }
}
