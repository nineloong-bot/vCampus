package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.governance.AssignModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.ModuleAdministratorView;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Page-embedded form for assigning a dedicated administrator to a module. */
public final class ModuleAdministratorEditorPanel implements EmbeddedEditor {
    private static final String[] MODULES = {"STUDENT", "COURSE", "LIBRARY", "SHOP", "USER"};
    private final JPanel root = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
    private final UserClientService users;
    private final ModuleAdministratorView target;
    private final Runnable saved;
    private final Runnable cancelled;
    private final JComboBox<String> module = new JComboBox<>(MODULES);
    private final JButton submit = named(new JButton("保存权限"), "governance.submit");
    private final JLabel error = named(new JLabel(" "), "governance.editor.error");
    private boolean active;

    /** Creates an embedded module assignment editor for the selected administrator. */
    public ModuleAdministratorEditorPanel(UserClientService users,
            ModuleAdministratorView target, Runnable saved, Runnable cancelled) {
        this.users = Objects.requireNonNull(users, "users");
        this.target = Objects.requireNonNull(target, "target");
        this.saved = Objects.requireNonNull(saved, "saved");
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(UiBorders.pageInset());
        root.setMinimumSize(new Dimension(380, 250));
        module.setName("governance.module");
        module.getAccessibleContext().setAccessibleName("目标模块");
        module.setSelectedItem(target.moduleCode());
        root.add(form(), BorderLayout.CENTER);
        root.add(actions(), BorderLayout.SOUTH);
        submit.addActionListener(event -> submit());
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return !target.moduleCode().equals(module.getSelectedItem()); }
    @Override public void onOpened() { active = true; }
    @Override public void onClosed() { active = false; }

    private JPanel form() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, UiSpacing.SPACE_3));
        panel.setOpaque(false);
        JLabel title = new JLabel("调整模块权限");
        title.setFont(UiTypography.PAGE_TITLE);
        panel.add(title);
        panel.add(new JLabel("管理员：" + target.loginId()));
        panel.add(new JLabel("目标模块"));
        panel.add(module);
        error.setForeground(UiColors.ERROR_FG);
        panel.add(error);
        return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        panel.setOpaque(false);
        JButton cancel = named(new JButton("取消"), "governance.editor.cancel");
        cancel.addActionListener(event -> cancelled.run());
        submit.setBackground(UiColors.ACCENT);
        submit.setForeground(UiColors.TEXT_ON_PRIMARY);
        panel.add(cancel);
        panel.add(submit);
        return panel;
    }

    private void submit() {
        String selected = (String) module.getSelectedItem();
        if (selected == null) { error.setText("请选择目标模块"); return; }
        setBusy(true);
        CompletableFuture<Void> response;
        try {
            response = users.assignModuleAdministrator(new AssignModuleAdministratorCommand(
                    selected, target.userId(), target.rowVersion()));
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        if (response == null) response = CompletableFuture.failedFuture(
                new IllegalStateException("Missing governance response"));
        response.whenComplete((ignored, failure) -> onEdt(() -> finish(failure)));
    }

    private void finish(Throwable failure) {
        if (!active) return;
        setBusy(false);
        if (failure != null) {
            error.setText(UserErrorMessages.operation(failure, "权限调整失败，请稍后重试"));
            return;
        }
        saved.run();
    }

    private void setBusy(boolean busy) {
        module.setEnabled(!busy);
        submit.setEnabled(!busy);
        submit.setText(busy ? "正在保存…" : "保存权限");
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
