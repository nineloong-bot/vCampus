package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSummary;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Verifies that account role changes use the shared page workspace. */
class UserManagementWorkspaceTest {
    @Test
    void roleEditorAppearsOnlyAfterActionAndClosesAfterSave() throws Exception {
        UserClientService users = mock(UserClientService.class);
        UserSummary teacher = new UserSummary("teacher-id", "200001", UserRole.TEACHER,
                AccountStatus.ACTIVE, null, 7);
        doReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(teacher), 0, 20, 1)))
                .when(users).searchUsers(any());
        doReturn(CompletableFuture.completedFuture(null)).when(users).updateRole(any());
        UserManagementPanel[] holder = new UserManagementPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new UserManagementPanel(users,
                Set.of("USER_READ_ALL", "USER_ROLE_WRITE")));
        flushEdt();

        EmbeddedEditorHost host = component(holder[0], EmbeddedEditorHost.class);
        assertThat(host.isEditorOpen()).isFalse();
        SwingUtilities.invokeAndWait(() -> {
            component(holder[0], "users.table", JTable.class).setRowSelectionInterval(0, 0);
            component(holder[0], "users.role.open", AbstractButton.class).doClick();
        });
        assertThat(host.isEditorOpen()).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            component(holder[0], "role.selection", JComboBox.class)
                    .setSelectedItem(UserRole.ADMIN);
            component(holder[0], "role.submit", AbstractButton.class).doClick();
        });
        flushEdt();

        verify(users).updateRole(new UpdateUserRoleCommand(
                "teacher-id", UserRole.ADMIN, 7));
        assertThat(host.isEditorOpen()).isFalse();
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static <T extends Component> T component(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T match = optional(nested, null, type);
                if (match != null) return match;
            }
        }
        throw new IllegalArgumentException("Missing " + type.getSimpleName());
    }

    private static <T extends Component> T component(
            Container root, String name, Class<T> type) {
        T match = optional(root, name, type);
        if (match != null) return match;
        throw new IllegalArgumentException("Missing " + name);
    }

    private static <T extends Component> T optional(
            Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if ((name == null || name.equals(child.getName())) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container nested) {
                T match = optional(nested, name, type);
                if (match != null) return match;
            }
        }
        return null;
    }
}
