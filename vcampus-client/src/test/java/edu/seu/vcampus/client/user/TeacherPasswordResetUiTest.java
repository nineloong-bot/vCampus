package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.UserManagementPanel;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ResetStudentPasswordCommand;
import edu.seu.vcampus.common.user.ResetTeacherPasswordCommand;
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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TeacherPasswordResetUiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 2, 9, 0);

    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows())
                .forEach(Window::dispose));
    }

    @Test
    void teacherOperationRequiresPermissionAndExplicitConfirmationThenRefreshes()
            throws Exception {
        UserClientService users = mock(UserClientService.class);
        UserSummary student = summary("student", UserRole.STUDENT, 1);
        UserSummary teacher = summary("teacher", UserRole.TEACHER, 2);
        UserSummary administrator = summary("admin", UserRole.ADMIN, 3);
        PageResult<UserSummary> page = new PageResult<>(
                List.of(student, teacher, administrator), 0, 20, 3);
        doReturn(CompletableFuture.completedFuture(page)).when(users).searchUsers(any());
        doReturn(CompletableFuture.completedFuture(view(teacher)))
                .when(users).resetTeacherPassword(any(ResetTeacherPasswordCommand.class));

        UserManagementPanel panel = panel(users, Set.of(
                "USER_READ_ALL", "USER_PASSWORD_RESET"));
        JTable table = component(panel, "users.table", JTable.class);
        AbstractButton reset = component(panel, "users.resetPassword", AbstractButton.class);

        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(1, 1));
        assertThat(reset.isVisible()).isTrue();
        JDialog first = openConfirmation(reset);
        assertThat(text(first)).contains("教师下次登录必须修改密码")
                .doesNotContain("12345678", "token", "hash", "salt");
        verify(users, never()).resetTeacherPassword(any());
        SwingUtilities.invokeAndWait(() -> component(
                first, "teacherReset.cancel", AbstractButton.class).doClick());
        verify(users, never()).resetTeacherPassword(any());

        JDialog second = openConfirmation(reset);
        SwingUtilities.invokeAndWait(() -> component(
                second, "teacherReset.confirm", AbstractButton.class).doClick());
        flushEdt();

        verify(users).resetTeacherPassword(new ResetTeacherPasswordCommand(
                teacher.userId(), teacher.rowVersion()));
        verify(users, atLeast(2)).searchUsers(any());

        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(2, 2));
        assertThat(reset.isVisible()).isFalse();
        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
        assertThat(reset.isVisible()).isTrue();
        verify(users, never()).resetStudentPassword(any(ResetStudentPasswordCommand.class));

        UserManagementPanel withoutPermission = panel(users, Set.of("USER_READ_ALL"));
        JTable otherTable = component(withoutPermission, "users.table", JTable.class);
        SwingUtilities.invokeAndWait(() -> otherTable.setRowSelectionInterval(1, 1));
        assertThat(find(withoutPermission, "users.resetPassword")).isNull();
    }

    @Test
    void teacherResetFailureIsSafeAndLeavesPanelUsable() throws Exception {
        UserClientService users = mock(UserClientService.class);
        UserSummary teacher = summary("teacher", UserRole.TEACHER, 2);
        doReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(teacher), 0, 20, 1)))
                .when(users).searchUsers(any());
        doReturn(CompletableFuture.failedFuture(new IllegalStateException(
                "SQLException secret token hash salt")))
                .when(users).resetTeacherPassword(any());
        UserManagementPanel panel = panel(users, Set.of(
                "USER_READ_ALL", "USER_PASSWORD_RESET"));
        JTable table = component(panel, "users.table", JTable.class);
        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
        AbstractButton reset = component(panel, "users.resetPassword", AbstractButton.class);

        JDialog dialog = openConfirmation(reset);
        SwingUtilities.invokeAndWait(() -> component(
                dialog, "teacherReset.confirm", AbstractButton.class).doClick());
        flushEdt();

        assertThat(reset.isEnabled()).isTrue();
        assertThat(text(panel)).contains("密码初始化失败，请稍后重试")
                .doesNotContain("SQLException", "secret", "token", "hash", "salt");
    }

    @Test
    void concurrentResetRefreshesRowVersionBeforeAdministratorRetries() throws Exception {
        UserClientService users = mock(UserClientService.class);
        UserSummary stale = summary("teacher", UserRole.TEACHER, 2);
        UserSummary fresh = summary("teacher", UserRole.TEACHER, 3);
        doReturn(CompletableFuture.completedFuture(
                        new PageResult<>(List.of(stale), 0, 20, 1)),
                CompletableFuture.completedFuture(
                        new PageResult<>(List.of(fresh), 0, 20, 1)),
                CompletableFuture.completedFuture(
                        new PageResult<>(List.of(fresh), 0, 20, 1)))
                .when(users).searchUsers(any());
        doReturn(CompletableFuture.failedFuture(
                        new IllegalArgumentException("COMMON_CONCURRENT_MODIFICATION")),
                CompletableFuture.completedFuture(view(fresh)))
                .when(users).resetTeacherPassword(any());

        UserManagementPanel panel = panel(users, Set.of(
                "USER_READ_ALL", "USER_PASSWORD_RESET"));
        JTable table = component(panel, "users.table", JTable.class);
        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
        AbstractButton reset = component(panel, "users.resetPassword", AbstractButton.class);

        confirm(openConfirmation(reset));
        flushEdt();

        assertThat(table.getValueAt(0, 4)).isEqualTo(3L);
        assertThat(text(panel)).contains("账户信息已更新，请重新选择后重试");
        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
        confirm(openConfirmation(reset));
        flushEdt();

        var commands = org.mockito.ArgumentCaptor.forClass(
                ResetTeacherPasswordCommand.class);
        verify(users, times(2)).resetTeacherPassword(commands.capture());
        assertThat(commands.getAllValues())
                .extracting(ResetTeacherPasswordCommand::expectedRowVersion)
                .containsExactly(2L, 3L);
    }

    private static UserManagementPanel panel(UserClientService users, Set<String> permissions)
            throws Exception {
        UserManagementPanel[] result = new UserManagementPanel[1];
        SwingUtilities.invokeAndWait(() -> result[0] =
                new UserManagementPanel(users, permissions));
        flushEdt();
        return result[0];
    }

    private static JDialog openConfirmation(AbstractButton reset) throws Exception {
        SwingUtilities.invokeLater(reset::doClick);
        flushEdt();
        return Arrays.stream(Window.getWindows()).filter(JDialog.class::isInstance)
                .map(JDialog.class::cast).filter(Window::isShowing).findFirst().orElseThrow();
    }

    private static void confirm(JDialog dialog) throws Exception {
        SwingUtilities.invokeAndWait(() -> component(
                dialog, "teacherReset.confirm", AbstractButton.class).doClick());
    }

    private static UserSummary summary(String id, UserRole role, long version) {
        return new UserSummary(id, id.toUpperCase(), role,
                AccountStatus.ACTIVE, NOW, version);
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
