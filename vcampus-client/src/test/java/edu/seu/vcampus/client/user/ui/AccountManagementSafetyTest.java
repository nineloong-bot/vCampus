package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.governance.ModuleAdministrationSnapshot;
import edu.seu.vcampus.common.governance.ModuleAdministratorView;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class AccountManagementSafetyTest {
    private static final Set<String> PERMISSIONS = Set.of("USER_READ_ALL", "USER_STATUS_WRITE");

    @Test void closedAccountDoesNotAcceptLatePersonalDetails() throws Exception {
        var users = mock(UserClientService.class);
        var response = new CompletableFuture<UserView>();
        doReturn(response).when(users).getCurrentUser();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new AccountPanel(users, view("before"), Set.of(), () -> { });
            panel.removeNotify();
            response.complete(view("after"));
            assertThat(labels(panel)).contains("before").doesNotContain("after");
        });
    }

    @Test void closedUserQueryDoesNotUpdateTable() throws Exception {
        var users = mock(UserClientService.class);
        var response = new CompletableFuture<PageResult<UserSummary>>();
        doReturn(response).when(users).searchUsers(any());
        SwingUtilities.invokeAndWait(() -> {
            var panel = new UserManagementPanel(users, PERMISSIONS);
            panel.removeNotify();
            response.complete(page(UserRole.STUDENT));
            assertThat(find(panel, "users.table", JTable.class).getRowCount()).isZero();
        });
    }

    @Test void closedStatusChangeDoesNotReloadAccounts() throws Exception {
        var users = mock(UserClientService.class);
        var response = new CompletableFuture<UserView>();
        doReturn(CompletableFuture.completedFuture(page(UserRole.STUDENT)))
                .when(users).searchUsers(any());
        doReturn(response).when(users).changeStatus(any());
        SwingUtilities.invokeAndWait(() -> {
            var panel = new UserManagementPanel(users, PERMISSIONS);
            find(panel, "users.table", JTable.class).setRowSelectionInterval(0, 0);
            find(panel, "users.status", AbstractButton.class).doClick();
            panel.removeNotify();
            response.complete(view("student"));
            verify(users, times(1)).searchUsers(any());
        });
    }

    @Test void administratorsCannotBeSelectedForOrdinaryStatusChanges() throws Exception {
        var users = mock(UserClientService.class);
        doReturn(CompletableFuture.completedFuture(page(UserRole.USER_ADMIN)))
                .when(users).searchUsers(any());
        SwingUtilities.invokeAndWait(() -> {
            var panel = new UserManagementPanel(users, PERMISSIONS);
            find(panel, "users.table", JTable.class).setRowSelectionInterval(0, 0);
            assertThat(find(panel, "users.status", AbstractButton.class).isEnabled()).isFalse();
        });
    }

    @Test void synchronousStatusFailureIsSafeAndAllowsRetry() throws Exception {
        var users = mock(UserClientService.class);
        doReturn(CompletableFuture.completedFuture(page(UserRole.STUDENT)))
                .when(users).searchUsers(any());
        doThrow(new IllegalStateException("SQL token password C:/internal"))
                .when(users).changeStatus(any());
        SwingUtilities.invokeAndWait(() -> {
            var panel = new UserManagementPanel(users, PERMISSIONS);
            find(panel, "users.table", JTable.class).setRowSelectionInterval(0, 0);
            assertThatCode(() -> find(panel, "users.status", AbstractButton.class).doClick())
                    .doesNotThrowAnyException();
            assertThat(labels(panel)).contains("状态修改失败，请稍后重试");
            assertThat(find(panel, "users.status", AbstractButton.class).isEnabled()).isTrue();
        });
    }

    @Test void synchronousGovernanceFailureIsSafeAndAllowsRetry() throws Exception {
        var users = governanceUsers();
        doThrow(new IllegalStateException("SQL token password C:/internal"))
                .when(users).assignModuleAdministrator(any());
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ModulePermissionManagementPanel(users, message -> true);
            find(panel, "governance.table", JTable.class).setRowSelectionInterval(0, 0);
            assertThatCode(() -> find(panel, "governance.assign", AbstractButton.class).doClick())
                    .doesNotThrowAnyException();
            assertThat(labels(panel)).contains("权限调整失败，请稍后重试");
            assertThat(find(panel, "governance.assign", AbstractButton.class).isEnabled()).isTrue();
        });
    }

    @Test void defaultGovernanceConfirmationOpensWithItsWindowOwner() throws Exception {
        var users = governanceUsers();
        SwingUtilities.invokeAndWait(() -> {
            var owner = new JFrame();
            var panel = new ModulePermissionManagementPanel(users);
            owner.add(panel);
            owner.pack();
            boolean[] opened = {false};
            var timer = new Timer(150, event -> {
                for (Window window : Window.getWindows()) {
                    if (window instanceof JDialog dialog && dialog.isShowing()) {
                        opened[0] = dialog.getOwner() == owner;
                        dialog.dispose();
                    }
                }
            });
            timer.start();
            try {
                find(panel, "governance.table", JTable.class).setRowSelectionInterval(0, 0);
                assertThatCode(() -> find(panel, "governance.assign", AbstractButton.class).doClick())
                        .doesNotThrowAnyException();
                assertThat(opened[0]).isTrue();
            } finally { timer.stop(); owner.dispose(); }
        });
    }

    @Test void closedAuditQueryDoesNotUpdateTable() throws Exception {
        var users = mock(UserClientService.class);
        var response = new CompletableFuture<PageResult<SecurityAuditView>>();
        doReturn(response).when(users).searchSecurityAudits(any());
        SwingUtilities.invokeAndWait(() -> {
            var panel = new SecurityAuditPanel(users);
            panel.removeNotify();
            response.complete(new PageResult<>(List.of(new SecurityAuditView(
                    "audit", "actor", "USER_CHANGE_STATUS", "USER", "student", "SUCCESS", null)),
                    0, 20, 1));
            assertThat(find(panel, "audit.table", JTable.class).getRowCount()).isZero();
        });
    }

    private static UserClientService governanceUsers() {
        var users = mock(UserClientService.class);
        doReturn(CompletableFuture.completedFuture(new ModuleAdministrationSnapshot(List.of(
                new ModuleAdministratorView("USER", "用户管理", UserRole.USER_ADMIN,
                        "user", "USER_ADMIN", AccountStatus.ACTIVE, 0)))))
                .when(users).listModuleAdministrators();
        return users;
    }

    private static UserView view(String loginId) {
        return new UserView("user", loginId, UserRole.STUDENT, AccountStatus.ACTIVE,
                false, null, 0, null, null);
    }
    private static PageResult<UserSummary> page(UserRole role) {
        return new PageResult<>(List.of(new UserSummary("user", "login", role,
                AccountStatus.ACTIVE, null, 0)), 0, 20, 1);
    }
    private static List<String> labels(Container root) {
        var result = new java.util.ArrayList<String>();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) result.add(label.getText());
            if (child instanceof Container nested) result.addAll(labels(nested));
        }
        return result;
    }
    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T result = find(nested, name, type);
                if (result != null) return result;
            }
        }
        return null;
    }
}
