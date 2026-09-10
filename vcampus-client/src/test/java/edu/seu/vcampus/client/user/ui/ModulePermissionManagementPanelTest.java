package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.governance.AssignModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.ModuleAdministrationSnapshot;
import edu.seu.vcampus.common.governance.ModuleAdministratorView;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** UI tests for super-administrator dedicated-role governance. */
class ModulePermissionManagementPanelTest {
    @Test
    void listsAssignmentsAndAdjustsASelectedAdministratorAsynchronously() throws Exception {
        UserClientService users = mock(UserClientService.class);
        ModuleAdministrationSnapshot snapshot = snapshot();
        doReturn(CompletableFuture.completedFuture(snapshot))
                .when(users).listModuleAdministrators();
        doReturn(CompletableFuture.completedFuture(null))
                .when(users).assignModuleAdministrator(any());
        ModulePermissionManagementPanel[] panel = new ModulePermissionManagementPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new ModulePermissionManagementPanel(
                users, message -> true));
        flushEdt();

        JTable table = component(panel[0], "governance.table", JTable.class);
        assertThat(table.getRowCount()).isEqualTo(5);
        assertThat(table.getValueAt(0, 0)).isEqualTo("学籍管理");
        SwingUtilities.invokeAndWait(() -> {
            table.setRowSelectionInterval(4, 4);
            component(panel[0], "governance.module", JComboBox.class)
                    .setSelectedItem("COURSE");
            component(panel[0], "governance.assign", AbstractButton.class).doClick();
        });
        flushEdt();

        verify(users).assignModuleAdministrator(
                new AssignModuleAdministratorCommand("COURSE", "user", 0));
        assertThat(component(panel[0], "governance.state", JLabel.class).getText())
                .doesNotContain("Exception", "token", "password");
    }

    @Test
    void cancelledRemovalDoesNotSendARequest() throws Exception {
        UserClientService users = mock(UserClientService.class);
        doReturn(CompletableFuture.completedFuture(snapshot()))
                .when(users).listModuleAdministrators();
        ModulePermissionManagementPanel[] panel = new ModulePermissionManagementPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new ModulePermissionManagementPanel(
                users, message -> false));
        flushEdt();

        SwingUtilities.invokeAndWait(() -> {
            JTable table = component(panel[0], "governance.table", JTable.class);
            table.setRowSelectionInterval(0, 0);
            component(panel[0], "governance.remove", AbstractButton.class).doClick();
        });

        org.mockito.Mockito.verify(users, org.mockito.Mockito.never())
                .removeModuleAdministrator(any());
    }

    private static ModuleAdministrationSnapshot snapshot() {
        return new ModuleAdministrationSnapshot(List.of(
                row("STUDENT", "学籍管理", UserRole.STUDENT_ADMIN, "student"),
                row("COURSE", "课程管理", UserRole.COURSE_ADMIN, "course"),
                row("LIBRARY", "图书管理", UserRole.LIBRARY_ADMIN, "library"),
                row("SHOP", "商城管理", UserRole.SHOP_ADMIN, "shop"),
                row("USER", "用户管理", UserRole.USER_ADMIN, "user")));
    }

    private static ModuleAdministratorView row(
            String module, String name, UserRole role, String user) {
        return new ModuleAdministratorView(module, name, role, user,
                user.toUpperCase() + "_ADMIN", AccountStatus.ACTIVE, 0);
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static <T extends Component> T component(
            Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                try {
                    return component(nested, name, type);
                } catch (IllegalArgumentException ignored) {
                    // Continue through siblings.
                }
            }
        }
        throw new IllegalArgumentException("Missing component " + name);
    }
}
