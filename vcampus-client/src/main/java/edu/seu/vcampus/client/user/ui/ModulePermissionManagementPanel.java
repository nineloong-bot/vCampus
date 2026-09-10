package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.governance.AssignModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.ModuleAdministrationSnapshot;
import edu.seu.vcampus.common.governance.ModuleAdministratorView;
import edu.seu.vcampus.common.governance.RemoveModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.SwapModuleAdministratorsCommand;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Operational page for super-administrator governance of dedicated module roles. */
public final class ModulePermissionManagementPanel extends JPanel {
    private final UserClientService users;
    private final Confirmation confirmation;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"模块", "管理员角色", "登录标识", "状态", "版本"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JComboBox<String> module = new JComboBox<>(
            new String[] {"STUDENT", "COURSE", "LIBRARY", "SHOP", "USER"});
    private final JButton refresh = button("刷新", "governance.refresh");
    private final JButton assign = button("调整权限", "governance.assign");
    private final JButton swap = button("交换权限", "governance.swap");
    private final JButton remove = button("停用资格", "governance.remove");
    private final JLabel state = new JLabel("正在加载权限分配…");
    private List<ModuleAdministratorView> rows = List.of();
    private boolean busy;
    private boolean closed;

    /** Creates the permissions page with a safe local confirmation dialog. */
    public ModulePermissionManagementPanel(UserClientService users) {
        this(users, null);
    }

    ModulePermissionManagementPanel(UserClientService users, Confirmation confirmation) {
        super(new BorderLayout(0, UiSpacing.SPACE_4));
        this.users = Objects.requireNonNull(users, "users");
        this.confirmation = confirmation != null ? confirmation
                : message -> JOptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(this), message, "确认权限调整",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                        == JOptionPane.YES_OPTION;
        setName("page.modulePermissionManagement");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        add(heading(), BorderLayout.NORTH);
        table.setName("governance.table");
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setRowHeight(34);
        table.getAccessibleContext().setAccessibleName("模块管理员权限列表");
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions(), BorderLayout.SOUTH);
        refresh.addActionListener(event -> load(null));
        assign.addActionListener(event -> assign());
        remove.addActionListener(event -> remove());
        swap.addActionListener(event -> swap());
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        updateButtons();
        load(null);
    }

    private JPanel heading() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel title = new JLabel("权限管理");
        title.setFont(UiTypography.PAGE_TITLE);
        panel.add(title, BorderLayout.WEST);
        JPanel target = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        target.setOpaque(false);
        target.add(new JLabel("调整为"));
        module.setName("governance.module");
        module.getAccessibleContext().setAccessibleName("目标模块");
        target.add(module);
        target.add(assign);
        panel.add(target, BorderLayout.EAST);
        return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        panel.setOpaque(false);
        state.setName("governance.state");
        panel.add(state);
        panel.add(refresh);
        panel.add(swap);
        panel.add(remove);
        return panel;
    }

    private void load(String successMessage) {
        if (closed) return;
        setBusy(true);
        CompletableFuture<ModuleAdministrationSnapshot> response;
        try {
            response = users.listModuleAdministrators();
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        if (response == null) {
            response = CompletableFuture.failedFuture(
                    new IllegalStateException("Missing governance response"));
        }
        response.whenComplete((result, failure) -> onEdt(() -> {
            if (closed) return;
            setBusy(false);
            if (failure != null || result == null) {
                state.setText(UserErrorMessages.operation(
                        failure, "权限分配加载失败，请重试"));
                return;
            }
            rows = result.administrators();
            model.setRowCount(0);
            for (ModuleAdministratorView row : rows) {
                model.addRow(new Object[] {row.moduleName(), row.administratorRole().name(),
                        row.loginId(), status(row), row.rowVersion()});
            }
            state.setText(successMessage == null ? "共 " + rows.size() + " 条" : successMessage);
        }));
    }

    private void assign() {
        if (busy || closed) return;
        ModuleAdministratorView selected = selectedOne();
        String target = (String) module.getSelectedItem();
        if (selected == null || target == null) {
            state.setText("请先选择一名管理员");
            return;
        }
        if (!confirmation.confirm("确定将该账号调整为 " + target + " 模块管理员吗？")) return;
        execute(() -> users.assignModuleAdministrator(new AssignModuleAdministratorCommand(
                target, selected.userId(), selected.rowVersion())), "权限调整成功");
    }

    private void remove() {
        if (busy || closed) return;
        ModuleAdministratorView selected = selectedOne();
        if (selected == null) {
            state.setText("请先选择一名管理员");
            return;
        }
        if (!confirmation.confirm("确定停用该账号的模块管理资格吗？")) return;
        execute(() -> users.removeModuleAdministrator(new RemoveModuleAdministratorCommand(
                selected.moduleCode(), selected.userId(), selected.rowVersion())),
                "模块管理资格已停用");
    }

    private void swap() {
        if (busy || closed) return;
        int[] selection = table.getSelectedRows();
        if (selection.length != 2) {
            state.setText("请选择两名管理员进行交换");
            return;
        }
        ModuleAdministratorView first = rows.get(selection[0]);
        ModuleAdministratorView second = rows.get(selection[1]);
        if (!confirmation.confirm("确定交换两名管理员负责的模块吗？")) return;
        execute(() -> users.swapModuleAdministrators(new SwapModuleAdministratorsCommand(
                first.moduleCode(), first.userId(), first.rowVersion(),
                second.moduleCode(), second.userId(), second.rowVersion())), "权限交换成功");
    }

    private void execute(java.util.function.Supplier<CompletableFuture<Void>> request,
                         String successMessage) {
        if (busy || closed) return;
        setBusy(true);
        CompletableFuture<Void> response;
        try { response = request.get(); }
        catch (RuntimeException failure) { response = CompletableFuture.failedFuture(failure); }
        if (response == null) response = CompletableFuture.failedFuture(
                new IllegalStateException("Missing governance response"));
        response.whenComplete((ignored, failure) -> onEdt(() -> {
            if (closed) return;
            if (failure == null) load(successMessage);
            else {
                setBusy(false);
                state.setText(UserErrorMessages.operation(
                        failure, "权限调整失败，请稍后重试"));
            }
        }));
    }

    private ModuleAdministratorView selectedOne() {
        int row = table.getSelectedRow();
        return row < 0 || row >= rows.size() ? null : rows.get(row);
    }

    private void setBusy(boolean value) {
        busy = value;
        refresh.setEnabled(!value);
        updateButtons();
        if (value) state.setText("正在处理…");
    }

    private void updateButtons() {
        int count = table.getSelectedRowCount();
        assign.setEnabled(!busy && count == 1);
        remove.setEnabled(!busy && count == 1);
        swap.setEnabled(!busy && count == 2);
    }

    @Override public void addNotify() {
        closed = false;
        super.addNotify();
    }

    @Override public void removeNotify() {
        closed = true;
        super.removeNotify();
    }

    private static String status(ModuleAdministratorView row) {
        return row.accountStatus() == edu.seu.vcampus.common.user.AccountStatus.ACTIVE
                ? "正常" : "已停用";
    }

    private static JButton button(String text, String name) {
        JButton button = new JButton(text);
        button.setName(name);
        button.getAccessibleContext().setAccessibleName(text);
        return button;
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    @FunctionalInterface
    interface Confirmation {
        boolean confirm(String message);
    }
}
