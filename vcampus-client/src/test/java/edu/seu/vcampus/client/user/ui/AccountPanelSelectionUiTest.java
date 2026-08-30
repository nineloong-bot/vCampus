package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.UserRole.ADMIN;
import static edu.seu.vcampus.common.user.UserRole.TEACHER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class AccountPanelSelectionUiTest {
    private static final Set<String> ADMIN_PERMISSIONS = Set.of(
            "USER_READ_ALL", "USER_ROLE_WRITE", "USER_STATUS_WRITE", "USER_AUDIT_READ");

    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows())
                .forEach(Window::dispose));
    }

    @Test
    void accountPageButtonsUseGreenSelectionAndNonRedKeyboardFocus() throws Exception {
        AccountPanel[] panel = new AccountPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new AccountPanel(
                service(), user(ADMIN), ADMIN_PERMISSIONS, () -> { }));
        flushEdt();
        AbstractButton detail = component(panel[0], "account.detail", AbstractButton.class);
        AbstractButton password = component(panel[0], "account.password", AbstractButton.class);
        AbstractButton users = component(panel[0], "account.users", AbstractButton.class);
        AbstractButton audit = component(panel[0], "account.audit", AbstractButton.class);

        assertSelected(detail);
        for (AbstractButton button : List.of(detail, password, users, audit)) {
            assertThat(button.isFocusPainted()).isFalse();
            assertThat(button.getBorder()).isNotSameAs(UiBorders.FOCUS);
        }

        SwingUtilities.invokeAndWait(users::doClick);
        assertSelected(users);
        assertUnselected(detail);
        SwingUtilities.invokeAndWait(audit::doClick);
        assertSelected(audit);
        assertUnselected(users);

        var normalBorder = audit.getBorder();
        SwingUtilities.invokeAndWait(() -> {
            for (FocusListener listener : audit.getFocusListeners()) {
                listener.focusGained(new FocusEvent(audit, FocusEvent.FOCUS_GAINED));
            }
        });
        assertThat(audit.getBorder()).isNotSameAs(normalBorder).isNotSameAs(UiBorders.FOCUS);
    }

    @Test
    void nonAdministratorCannotSeeManagementOrAuditEvenWithPermissionStrings()
            throws Exception {
        AccountPanel[] panel = new AccountPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new AccountPanel(
                service(), user(TEACHER), ADMIN_PERMISSIONS, () -> { }));
        flushEdt();

        assertThat(find(panel[0], "account.users")).isNull();
        assertThat(find(panel[0], "account.audit")).isNull();
    }

    @Test
    void passwordDialogIsTheOnlySelectedActionWhileOpenAndRestoresCurrentPage()
            throws Exception {
        AccountPanel[] panel = new AccountPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new AccountPanel(
                service(), user(ADMIN), ADMIN_PERMISSIONS, () -> { }));
        flushEdt();
        AbstractButton detail = component(panel[0], "account.detail", AbstractButton.class);
        AbstractButton password = component(panel[0], "account.password", AbstractButton.class);
        AtomicBoolean exclusiveSelection = new AtomicBoolean();
        java.awt.event.AWTEventListener listener = event -> {
            if (event instanceof WindowEvent windowEvent
                    && windowEvent.getID() == WindowEvent.WINDOW_OPENED
                    && windowEvent.getWindow() instanceof ChangePasswordDialog dialog) {
                exclusiveSelection.set(password.isSelected() && !detail.isSelected()
                        && password.getBackground().equals(UiColors.PRIMARY));
                SwingUtilities.invokeLater(dialog::dispose);
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK);
        try {
            SwingUtilities.invokeAndWait(password::doClick);
        } finally {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
        }

        assertThat(exclusiveSelection).isTrue();
        assertSelected(detail);
        assertUnselected(password);
    }

    private static UserClientService service() {
        UserClientService users = mock(UserClientService.class);
        doReturn(CompletableFuture.completedFuture(user(ADMIN))).when(users).getCurrentUser();
        doReturn(CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 20, 0)))
                .when(users).searchUsers(any());
        doReturn(CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 20, 0)))
                .when(users).searchSecurityAudits(any());
        return users;
    }

    private static UserView user(edu.seu.vcampus.common.user.UserRole role) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 30, 12, 0);
        return new UserView("user", role.name(), role, ACTIVE, false, now, 0, now, now);
    }

    private static void assertSelected(AbstractButton button) {
        assertThat(button.isSelected()).isTrue();
        assertThat(button.getBackground()).isEqualTo(UiColors.PRIMARY);
        assertThat(button.getForeground()).isEqualTo(UiColors.TEXT_ON_PRIMARY);
    }

    private static void assertUnselected(AbstractButton button) {
        assertThat(button.isSelected()).isFalse();
        assertThat(button.getBackground()).isNotEqualTo(UiColors.PRIMARY);
        assertThat(button.getForeground()).isEqualTo(UiColors.TEXT_PRIMARY);
    }

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
        Component found = find(root, name);
        if (!type.isInstance(found)) throw new IllegalArgumentException("Missing " + name);
        return type.cast(found);
    }

    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }
}
