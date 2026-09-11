package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.client.user.ui.UserManagementPanel;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSummary;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class TeacherAccountRetirementUiTest {
    @Test
    void pendingTeacherHasNoApprovalAction() throws Exception {
        UserClientService users = mock(UserClientService.class);
        UserSummary pending = new UserSummary("teacher", "TEACHER", UserRole.TEACHER,
                AccountStatus.PENDING, LocalDateTime.MIN, 0);
        doReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(pending), 0, 20, 1)))
                .when(users).searchUsers(any());

        UserManagementPanel[] panel = new UserManagementPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new UserManagementPanel(
                users, Set.of("USER_READ_ALL", "USER_STATUS_WRITE")));
        flushEdt();
        JTable table = component(panel[0], "users.table", JTable.class);
        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));

        AbstractButton status = component(panel[0], "users.status", AbstractButton.class);
        assertThat(status.isEnabled()).isFalse();
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
                    // Search the next sibling.
                }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }
}
