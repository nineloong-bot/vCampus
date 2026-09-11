package edu.seu.vcampus.client.user.ui;

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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Administrator dialog that only switches existing teacher/admin accounts. */
public final class UserRoleDialog extends JDialog {
    private final UserClientService users;
    private final UserSummary target;
    private final Runnable onUpdated;
    private final JComboBox<UserRole> role = new JComboBox<>(
            new UserRole[]{UserRole.TEACHER, UserRole.ADMIN});
    private final JButton submit = named(new JButton("保存角色"), "role.submit");
    private final JLabel error = named(new JLabel(" "), "role.error");

    /** Creates a guarded role adjustment dialog. */
    public UserRoleDialog(Window owner, UserClientService users,
                          UserSummary target, Runnable onUpdated) {
        super(owner, "调整账户角色", Dialog.ModalityType.APPLICATION_MODAL);
        this.users = Objects.requireNonNull(users, "users");
        this.target = Objects.requireNonNull(target, "target");
        this.onUpdated = Objects.requireNonNull(onUpdated, "onUpdated");
        setContentPane(content());
        role.setName("role.selection");
        role.getAccessibleContext().setAccessibleName("目标角色");
        role.setSelectedItem(target.role());
        submit.addActionListener(event -> submit());
        boolean student = target.role() == UserRole.STUDENT;
        submit.setEnabled(!student);
        role.setEnabled(!student);
        if (student) error.setText("学生账户不能调整角色");
        setSize(420, 260);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JPanel content() {
        JPanel panel = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        panel.setBackground(UiColors.BACKGROUND_PAGE);
        panel.setBorder(UiBorders.pageInset());
        JLabel title = new JLabel("调整账户角色");
        title.setFont(UiTypography.PAGE_TITLE);
        panel.add(title, BorderLayout.NORTH);
        JPanel center = new JPanel(new GridLayout(0, 1, 0, UiSpacing.SPACE_3));
        center.setOpaque(false);
        center.add(new JLabel("账户：" + target.loginId()));
        center.add(new JLabel("目标角色（仅教师/管理员）"));
        center.add(role);
        error.setForeground(UiColors.ERROR_FG);
        center.add(error);
        panel.add(center, BorderLayout.CENTER);
        submit.setBackground(UiColors.ACCENT);
        submit.setForeground(UiColors.TEXT_ON_PRIMARY);
        panel.add(submit, BorderLayout.SOUTH);
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
        setBusy(false);
        if (failure != null) {
            error.setText(UserErrorMessages.operation(failure, "角色修改失败，请稍后重试"));
            return;
        }
        dispose();
        onUpdated.run();
    }

    private void setBusy(boolean busy) {
        submit.setEnabled(!busy);
        role.setEnabled(!busy);
        submit.setText(busy ? "正在保存…" : "保存角色");
    }
    private static <T extends javax.swing.JComponent> T named(T component, String name) {
        component.setName(name); component.getAccessibleContext().setAccessibleName(name);
        return component;
    }
    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
