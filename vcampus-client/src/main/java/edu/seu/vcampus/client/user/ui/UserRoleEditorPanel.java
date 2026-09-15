package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSummary;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Page-embedded editor for guarded teacher and administrator role changes. */
public final class UserRoleEditorPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
    private final UserClientService users;
    private final UserSummary target;
    private final Runnable saved;
    private final Runnable cancelled;
    private final JComboBox<UserRole> role = new JComboBox<>(
            new UserRole[]{UserRole.TEACHER, UserRole.ADMIN});
    private final JButton submit = named(new JButton("保存角色"), "role.submit");
    private final JLabel error = named(new JLabel(" "), "role.error");
    private boolean active;

    /** Creates an account role editor attached to the current management page. */
    public UserRoleEditorPanel(UserClientService users, UserSummary target,
            Runnable saved, Runnable cancelled) {
        this.users = Objects.requireNonNull(users, "users");
        this.target = Objects.requireNonNull(target, "target");
        this.saved = Objects.requireNonNull(saved, "saved");
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(UiBorders.pageInset());
        root.setMinimumSize(new Dimension(380, 250));
        role.setName("role.selection");
        role.getAccessibleContext().setAccessibleName("目标角色");
        role.setSelectedItem(target.role());
        root.add(form(), BorderLayout.CENTER);
        root.add(actions(), BorderLayout.SOUTH);
        submit.addActionListener(event -> submit());
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return role.getSelectedItem() != target.role(); }
    @Override public void onOpened() { active = true; }
    @Override public void onClosed() { active = false; }

    private JPanel form() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, UiSpacing.SPACE_3));
        panel.setOpaque(false);
        JLabel title = new JLabel("调整账户角色");
        title.setFont(UiTypography.PAGE_TITLE);
        panel.add(title);
        panel.add(new JLabel("账户：" + target.loginId()));
        panel.add(new JLabel("目标角色（仅教师/管理员）"));
        panel.add(role);
        error.setForeground(UiColors.ERROR_FG);
        panel.add(error);
        return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        panel.setOpaque(false);
        JButton cancel = named(new JButton("取消"), "role.cancel");
        cancel.addActionListener(event -> cancelled.run());
        submit.setBackground(UiColors.ACCENT);
        submit.setForeground(UiColors.TEXT_ON_PRIMARY);
        panel.add(cancel);
        panel.add(submit);
        return panel;
    }

    private void submit() {
        UserRole selected = (UserRole) role.getSelectedItem();
        if (selected == null || selected == UserRole.STUDENT) {
            error.setText("只能选择教师或管理员");
            return;
        }
        setBusy(true);
        CompletableFuture<?> response;
        try {
            response = users.updateRole(new UpdateUserRoleCommand(
                    target.userId(), selected, target.rowVersion()));
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((ignored, failure) -> onEdt(() -> finish(failure)));
    }

    private void finish(Throwable failure) {
        if (!active) return;
        setBusy(false);
        if (failure != null) {
            error.setText(UserErrorMessages.operation(failure, "角色修改失败，请稍后重试"));
            return;
        }
        saved.run();
    }

    private void setBusy(boolean busy) {
        submit.setEnabled(!busy);
        role.setEnabled(!busy);
        submit.setText(busy ? "正在保存…" : "保存角色");
    }

    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name);
        component.getAccessibleContext().setAccessibleName(name);
        return component;
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
