package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
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
    private final JComboBox<Object> role = new JComboBox<>(filterValues(UserRole.values()));
    private final JComboBox<Object> status = new JComboBox<>(filterValues(AccountStatus.values()));
    private final JButton search = button("查询", "users.search");
    private final JButton changeRole = button("调整角色", "users.role");
    private final JButton changeStatus = button("变更状态", "users.status");
    private final JLabel state = new JLabel("准备查询");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"登录标识", "角色", "状态", "最近登录", "版本"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<UserSummary> rows = List.of();
    private int page;

    /** Creates a paged management page using the authenticated permission snapshot. */
    public UserManagementPanel(UserClientService users, Set<String> permissions) {
        super(new BorderLayout(0, UiSpacing.SPACE_4));
        this.users = Objects.requireNonNull(users, "users");
        this.permissions = Set.copyOf(permissions);
        setBackground(UiColors.BACKGROUND_PAGE); setBorder(UiBorders.pageInset());
        setName("page.userManagement");
        add(heading(), BorderLayout.NORTH);
        table.setName("users.table"); table.setRowHeight(34);
        table.getAccessibleContext().setAccessibleName("账户查询结果");
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions(), BorderLayout.SOUTH);
        search.addActionListener(event -> { page = 0; load(); });
        changeRole.addActionListener(event -> openRole());
        changeStatus.addActionListener(event -> changeStatus());
        load();
    }

    private JPanel heading() {
        JPanel panel = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
        panel.setOpaque(false);
        JLabel title = new JLabel("账户管理"); title.setFont(UiTypography.PAGE_TITLE);
        panel.add(title, BorderLayout.NORTH);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        filters.setBackground(UiColors.BACKGROUND_SUBTLE);
        keyword.setName("users.keyword");
        filters.add(new JLabel("登录标识")); filters.add(keyword);
        filters.add(new JLabel("角色")); filters.add(role);
        filters.add(new JLabel("状态")); filters.add(status); filters.add(search);
        panel.add(filters, BorderLayout.CENTER); return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        panel.setOpaque(false); panel.add(state);
        if (permissions.contains("USER_ROLE_WRITE")) panel.add(changeRole);
        if (permissions.contains("USER_STATUS_WRITE")) panel.add(changeStatus);
        return panel;
    }

    private void load() {
        if (!permissions.contains("USER_READ_ALL")) {
            state.setText("无权查看全部账户"); return;
        }
        setBusy(true);
        UserSearchQuery query = new UserSearchQuery(keyword.getText(), selected(role, UserRole.class),
                selected(status, AccountStatus.class), page, PAGE_SIZE);
        CompletableFuture<PageResult<UserSummary>> response;
        try { response = users.searchUsers(query); }
        catch (RuntimeException failure) { response = CompletableFuture.failedFuture(failure); }
        response.whenComplete((result, failure) -> onEdt(() -> finish(result, failure)));
    }

    private void finish(PageResult<UserSummary> result, Throwable failure) {
        setBusy(false); model.setRowCount(0);
        if (failure != null || result == null) {
            state.setText(UserErrorMessages.operation(failure, "账户列表加载失败，请重试")); return;
        }
        rows = List.copyOf(result.items());
        for (UserSummary row : rows) model.addRow(new Object[]{row.loginId(),
                roleName(row.role()), statusName(row.accountStatus()), row.lastLoginAt(), row.rowVersion()});
        state.setText(result.total() == 0 ? "未找到符合条件的账户" : "共 " + result.total() + " 条");
    }

    private void openRole() {
        UserSummary selected = selectedRow();
        if (selected == null) { state.setText("请先选择账户"); return; }
        UserRoleDialog dialog = new UserRoleDialog(
                SwingUtilities.getWindowAncestor(this), users, selected, this::load);
        dialog.setVisible(true);
    }

    private void changeStatus() {
        UserSummary selected = selectedRow();
        if (selected == null) { state.setText("请先选择账户"); return; }
        AccountStatus next = nextStatus(selected.accountStatus());
        if (next == null) { state.setText("当前状态不能在此变更"); return; }
        setBusy(true);
        users.changeStatus(new ChangeUserStatusCommand(selected.userId(), next,
                "管理员账户管理操作", selected.rowVersion()))
                .whenComplete((ignored, failure) -> onEdt(() -> {
                    if (failure == null) load();
                    else { setBusy(false); state.setText(UserErrorMessages.operation(
                            failure, "状态修改失败，请稍后重试")); }
                }));
    }

    private UserSummary selectedRow() {
        int index = table.getSelectedRow();
        return index < 0 || index >= rows.size() ? null : rows.get(index);
    }
    private void setBusy(boolean busy) {
        search.setEnabled(!busy); changeRole.setEnabled(!busy); changeStatus.setEnabled(!busy);
        if (busy) state.setText("正在加载…");
    }
    private static AccountStatus nextStatus(AccountStatus current) {
        return switch (current) {
            case PENDING, DISABLED -> AccountStatus.ACTIVE;
            case ACTIVE -> AccountStatus.DISABLED;
            default -> null;
        };
    }
    private static String roleName(UserRole value) {
        return switch (value) { case STUDENT -> "学生"; case TEACHER -> "教师"; case ADMIN -> "管理员"; };
    }
    private static String statusName(AccountStatus value) {
        return switch (value) { case ACTIVE -> "正常"; case PENDING -> "待审核";
            case DISABLED -> "已停用"; case CANCELLED -> "已注销"; };
    }
    private static Object[] filterValues(Object[] values) {
        List<Object> result = new ArrayList<>(); result.add("全部"); result.addAll(List.of(values));
        return result.toArray();
    }
    private static <T> T selected(JComboBox<Object> box, Class<T> type) {
        return type.isInstance(box.getSelectedItem()) ? type.cast(box.getSelectedItem()) : null;
    }
    private static JButton button(String text, String name) {
        JButton button = new JButton(text); button.setName(name);
        button.getAccessibleContext().setAccessibleName(text); return button;
    }
    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run(); else SwingUtilities.invokeLater(action);
    }
}
