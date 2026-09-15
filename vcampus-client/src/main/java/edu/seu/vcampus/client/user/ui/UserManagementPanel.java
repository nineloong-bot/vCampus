package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Permission-aware account management query-list page. */
public final class UserManagementPanel extends JPanel {
    private static final int PAGE_SIZE = 20;
    private final UserClientService users;
    private final Set<String> permissions;
    private final JTextField keyword = new JTextField(14);
    private final JComboBox<Object> roleFilter = new JComboBox<>(
            UserManagementLabels.filters(UserRole.values()));
    private final JComboBox<Object> statusFilter = new JComboBox<>(
            UserManagementLabels.filters(AccountStatus.values()));
    private final JButton search = button("查询", "users.search");
    private final JButton role = button("调整角色", "users.role.open");
    private final JButton changeStatus = button("变更状态", "users.status");
    private final JButton resetPassword = button("初始化密码", "users.resetPassword");
    private final JLabel state = new JLabel("准备查询");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"登录标识", "角色", "状态", "最近登录", "版本"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final UserDetailPanel detail = new UserDetailPanel();
    private final EmbeddedEditorHost editorHost;
    private final UserManagementActions actions;
    private List<UserSummary> rows = List.of();
    private int page;
    private boolean busy;
    private boolean closed;

    /** Creates a paged management page using the authenticated permission snapshot. */
    public UserManagementPanel(UserClientService users, Set<String> permissions) {
        super(new BorderLayout());
        this.users = Objects.requireNonNull(users, "users");
        this.permissions = Set.copyOf(permissions);
        actions = new UserManagementActions(users, this, () -> closed,
                this::setBusy, this::load, state::setText);
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        setName("page.userManagement");
        table.setName("users.table");
        table.setRowHeight(34);
        table.getAccessibleContext().setAccessibleName("账户查询结果");
        editorHost = new EmbeddedEditorHost(listing());
        add(editorHost, BorderLayout.CENTER);
        wireActions();
        resetPassword.setVisible(false);
        updateActionAvailability();
        load(null);
    }

    private JPanel listing() {
        JPanel left = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
        left.setOpaque(false);
        left.add(heading(), BorderLayout.NORTH);
        left.add(new JScrollPane(table), BorderLayout.CENTER);
        left.add(actionBar(), BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, detail);
        split.setDividerSize(6);
        split.setResizeWeight(0.58);
        split.setOneTouchExpandable(true);
        split.setBorder(null);
        split.setOpaque(false);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(split);
        return panel;
    }

    private JPanel heading() {
        JPanel panel = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
        panel.setOpaque(false);
        JLabel title = new JLabel("账户管理");
        title.setFont(UiTypography.PAGE_TITLE);
        panel.add(title, BorderLayout.NORTH);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        filters.setBackground(UiColors.BACKGROUND_SUBTLE);
        keyword.setName("users.keyword");
        filters.add(new JLabel("登录标识")); filters.add(keyword);
        filters.add(new JLabel("角色")); filters.add(roleFilter);
        filters.add(new JLabel("状态")); filters.add(statusFilter); filters.add(search);
        panel.add(filters, BorderLayout.CENTER);
        return panel;
    }

    private JPanel actionBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        panel.setOpaque(false);
        panel.add(state);
        if (permissions.contains("USER_ROLE_WRITE")) panel.add(role);
        if (permissions.contains("USER_STATUS_WRITE")) panel.add(changeStatus);
        if (permissions.contains("USER_PASSWORD_RESET")) panel.add(resetPassword);
        return panel;
    }

    private void wireActions() {
        search.addActionListener(event -> { page = 0; load(null); });
        role.addActionListener(event -> openRoleEditor());
        changeStatus.addActionListener(event -> actions.changeStatus(selectedRow()));
        resetPassword.addActionListener(event -> actions.confirmPasswordReset(selectedRow()));
        table.getSelectionModel().addListSelectionListener(event -> {
            updateActionAvailability();
            detail.showUser(selectedRow());
        });
    }

    private void openRoleEditor() {
        UserSummary selected = selectedRow();
        if (!roleWritable(selected)) { state.setText("请选择教师或管理员账户"); return; }
        editorHost.showEditor(new UserRoleEditorPanel(users, selected, () -> {
            editorHost.completeAndClose();
            load("角色修改成功");
        }, editorHost::requestClose));
    }

    private void load(String message) {
        if (closed) return;
        if (!permissions.contains("USER_READ_ALL")) {
            state.setText("无权查看全部账户");
            return;
        }
        setBusy(true);
        UserSearchQuery query = new UserSearchQuery(keyword.getText(),
                UserManagementLabels.selected(roleFilter, UserRole.class),
                UserManagementLabels.selected(statusFilter, AccountStatus.class), page, PAGE_SIZE);
        CompletableFuture<PageResult<UserSummary>> response;
        try { response = users.searchUsers(query); }
        catch (RuntimeException failure) { response = CompletableFuture.failedFuture(failure); }
        response.whenComplete((result, failure) -> onEdt(() -> finish(result, failure, message)));
    }

    private void finish(PageResult<UserSummary> result, Throwable failure, String message) {
        if (closed) return;
        setBusy(false); model.setRowCount(0); rows = List.of();
        if (failure != null || result == null) {
            state.setText(UserErrorMessages.operation(failure, "账户列表加载失败，请重试"));
            return;
        }
        rows = List.copyOf(result.items());
        for (UserSummary row : rows) model.addRow(new Object[]{row.loginId(),
                UserManagementLabels.role(row.role()), UserManagementLabels.status(row.accountStatus()),
                row.lastLoginAt(), row.rowVersion()});
        detail.clear();
        state.setText(message != null ? message : result.total() == 0
                ? "未找到符合条件的账户" : "共 " + result.total() + " 条");
    }

    private UserSummary selectedRow() {
        int index = table.getSelectedRow();
        return index < 0 || index >= rows.size() ? null : rows.get(index);
    }

    private void setBusy(boolean value) {
        busy = value;
        search.setEnabled(!value);
        resetPassword.setEnabled(!value);
        updateActionAvailability();
        if (value) state.setText("正在加载…");
    }

    private void updateActionAvailability() {
        UserSummary selected = selectedRow();
        role.setEnabled(!busy && roleWritable(selected));
        changeStatus.setEnabled(!busy && selected != null && UserManagementActions.ordinary(selected)
                && UserManagementActions.nextStatus(selected.accountStatus()) != null);
        resetPassword.setVisible(permissions.contains("USER_PASSWORD_RESET") && selected != null
                && (selected.role() == UserRole.STUDENT || selected.role() == UserRole.TEACHER));
    }

    @Override public void addNotify() { closed = false; super.addNotify(); }
    @Override public void removeNotify() { closed = true; super.removeNotify(); }
    private static boolean roleWritable(UserSummary user) {
        return user != null && (user.role() == UserRole.TEACHER || user.role() == UserRole.ADMIN);
    }

    private static JButton button(String text, String name) {
        JButton button = new JButton(text); button.setName(name);
        button.getAccessibleContext().setAccessibleName(text); return button;
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
