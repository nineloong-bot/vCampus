package edu.seu.vcampus.client.user;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.AccountPanel;
import edu.seu.vcampus.client.user.ui.ChangePasswordDialog;
import edu.seu.vcampus.client.user.ui.SecurityAuditPanel;
import edu.seu.vcampus.client.user.ui.TeacherAccountApplicationDialog;
import edu.seu.vcampus.client.user.ui.UserManagementPanel;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.SecurityAuditView;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
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
import java.util.concurrent.atomic.AtomicBoolean;
import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static edu.seu.vcampus.common.user.UserRole.STUDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
class Task6UserPagesUiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 30, 10, 0);

    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows())
                .forEach(Window::dispose));
    }

    @Test
    void teacherApplicationHasNoRoleChoiceAndClearsPasswordsOnSuccess() throws Exception {
        UserClientService users = mock(UserClientService.class);
        doReturn(CompletableFuture.completedFuture(view("teacher", ADMIN)))
                .when(users).applyForTeacherAccount(any(), any(char[].class));
        AtomicBoolean submitted = new AtomicBoolean();
        TeacherAccountApplicationDialog[] dialog = new TeacherAccountApplicationDialog[1];
        SwingUtilities.invokeAndWait(() -> dialog[0] = new TeacherAccountApplicationDialog(
                null, users, () -> submitted.set(true)));

        SwingUtilities.invokeAndWait(() -> {
            component(dialog[0], "teacher.loginId", JTextField.class).setText("teacher");
            component(dialog[0], "teacher.password", JPasswordField.class)
                    .setText("Teacher123456");
            component(dialog[0], "teacher.confirm", JPasswordField.class)
                    .setText("Teacher123456");
            component(dialog[0], "teacher.submit", AbstractButton.class).doClick();
        });
        flushEdt();

        verify(users).applyForTeacherAccount(org.mockito.ArgumentMatchers.eq("teacher"),
                any(char[].class));
        assertThat(submitted).isTrue();
        assertThat(text(dialog[0])).doesNotContain("角色", "ADMIN", "STUDENT");
        assertThat(component(dialog[0], "teacher.password", JPasswordField.class)
                .getPassword()).isEmpty();
    }

    @Test
    void accountShowsOnlySafeCurrentDetailsAndHidesAdminToolsWithoutPermissions()
            throws Exception {
        UserClientService users = mock(UserClientService.class);
        UserView current = view("DEMO_TEACHER", edu.seu.vcampus.common.user.UserRole.TEACHER);
        doReturn(CompletableFuture.completedFuture(current)).when(users).getCurrentUser();
        AccountPanel[] panel = new AccountPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new AccountPanel(
                users, current, Set.of(), () -> { }));
        flushEdt();

        assertThat(text(panel[0])).contains("DEMO_TEACHER", "教师", "正常", "最近登录")
                .doesNotContain("passwordHash", "salt", "sessionToken", "失败次数",
                        "是否首次改密");
        assertThat(find(panel[0], "account.users")).isNull();
        assertThat(find(panel[0], "account.audit")).isNull();
    }

    @Test
    void ordinaryPasswordChangeClearsFieldsAndRequestsRelogin() throws Exception {
        UserClientService users = mock(UserClientService.class);
        doReturn(CompletableFuture.completedFuture(null))
                .when(users).changePassword(any(char[].class), any(char[].class));
        AtomicBoolean changed = new AtomicBoolean();
        ChangePasswordDialog[] dialog = new ChangePasswordDialog[1];
        SwingUtilities.invokeAndWait(() -> dialog[0] = new ChangePasswordDialog(
                null, users, () -> changed.set(true)));

        fillPasswordDialog(dialog[0]);
        flushEdt();

        assertThat(changed).isTrue();
        assertThat(component(dialog[0], "change.old", JPasswordField.class).getPassword())
                .isEmpty();
        assertThat(component(dialog[0], "change.new", JPasswordField.class).getPassword())
                .isEmpty();
    }

    @Test
    void managementAndAuditPanelsIssuePagedFilteredQueriesAndExcludeSensitiveData()
            throws Exception {
        UserClientService users = mock(UserClientService.class);
        UserSummary student = new UserSummary("student", "213242478", STUDENT,
                ACTIVE, NOW, 2);
        doReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(student), 0, 20, 1)))
                .when(users).searchUsers(any(UserSearchQuery.class));
        SecurityAuditView audit = new SecurityAuditView("audit", "admin",
                "USER_LOGIN", "USER", "student", "SUCCESS", NOW);
        doReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(audit), 0, 20, 1)))
                .when(users).searchSecurityAudits(any(SecurityAuditQuery.class));
        UserManagementPanel[] management = new UserManagementPanel[1];
        SecurityAuditPanel[] audits = new SecurityAuditPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            management[0] = new UserManagementPanel(users,
                    Set.of("USER_READ_ALL", "USER_ROLE_WRITE", "USER_STATUS_WRITE"));
            audits[0] = new SecurityAuditPanel(users);
        });
        flushEdt();

        JTable userTable = component(management[0], "users.table", JTable.class);
        assertThat(userTable.getValueAt(0, 0)).isEqualTo("213242478");
        assertThat(userTable.getValueAt(0, 1)).isEqualTo("学生");
        JTable auditTable = component(audits[0], "audit.table", JTable.class);
        assertThat(Arrays.asList(auditTable.getColumnName(0), auditTable.getColumnName(1),
                auditTable.getColumnName(2), auditTable.getColumnName(3),
                auditTable.getColumnName(4), auditTable.getColumnName(5),
                auditTable.getColumnName(6)))
                .containsExactly("审计编号", "操作者", "动作", "目标类型", "目标", "结果", "时间")
                .doesNotContain("clientAddress", "password", "token");
        assertThat(auditTable.getValueAt(0, 2)).isEqualTo("USER_LOGIN");
        verify(users).searchUsers(any(UserSearchQuery.class));
        verify(users).searchSecurityAudits(any(SecurityAuditQuery.class));

        assertThat(find(management[0], "users.role")).isNull();
    }

    private static void fillPasswordDialog(ChangePasswordDialog dialog) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            component(dialog, "change.old", JPasswordField.class).setText("OldPassword7");
            component(dialog, "change.new", JPasswordField.class).setText("NewPassword8");
            component(dialog, "change.confirm", JPasswordField.class).setText("NewPassword8");
            component(dialog, "change.submit", AbstractButton.class).doClick();
        });
    }

    private static UserView view(String loginId, edu.seu.vcampus.common.user.UserRole role) {
        return new UserView("user", loginId, role, ACTIVE, false, NOW, 0, NOW, NOW);
    }

    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }

    private static Component find(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) {
                Component match = find(nested, name);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        Component match = find(root, name);
        if (!type.isInstance(match)) throw new IllegalArgumentException("Missing " + name);
        return type.cast(match);
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
}
