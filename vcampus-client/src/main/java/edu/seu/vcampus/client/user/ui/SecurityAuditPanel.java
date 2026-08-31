package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.SecurityAuditView;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Administrator query-list page for sanitized security audit records. */
public final class SecurityAuditPanel extends JPanel {
    private static final int PAGE_SIZE = 20;
    private final UserClientService users;
    private final JTextField userId = field("audit.user", "用户编号");
    private final JTextField action = field("audit.action", "动作代码");
    private final JTextField result = field("audit.result", "结果代码");
    private final JTextField from = field("audit.from", "开始时间");
    private final JTextField to = field("audit.to", "结束时间");
    private final JButton search = button("查询", "audit.search");
    private final JButton previous = button("上一页", "audit.previous");
    private final JButton next = button("下一页", "audit.next");
    private final JLabel state = new JLabel("准备查询");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"审计编号", "操作者", "动作", "目标类型", "目标", "结果", "时间"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private int page;

    /** Creates the audit query page and loads its first page asynchronously. */
    public SecurityAuditPanel(UserClientService users) {
        super(new BorderLayout(0, UiSpacing.SPACE_4));
        this.users = Objects.requireNonNull(users, "users");
        setName("page.securityAudit");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        add(heading(), BorderLayout.NORTH);
        JTable table = new JTable(model);
        table.setName("audit.table");
        table.getAccessibleContext().setAccessibleName("安全审计结果");
        table.setRowHeight(34);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(paging(), BorderLayout.SOUTH);
        search.addActionListener(event -> { page = 0; load(); });
        previous.addActionListener(event -> { if (page > 0) { page--; load(); } });
        next.addActionListener(event -> { page++; load(); });
        load();
    }

    private JPanel heading() {
        JPanel heading = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
        heading.setOpaque(false);
        JLabel title = new JLabel("安全审计");
        title.setFont(UiTypography.PAGE_TITLE);
        heading.add(title, BorderLayout.NORTH);
        JPanel filters = new JPanel(new GridLayout(2, 5, UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        filters.setBackground(UiColors.BACKGROUND_SUBTLE);
        filters.add(labeled("用户", userId)); filters.add(labeled("动作", action));
        filters.add(labeled("结果", result)); filters.add(labeled("开始时间", from));
        filters.add(labeled("结束时间", to));
        filters.add(search);
        heading.add(filters, BorderLayout.CENTER);
        return heading;
    }

    private JPanel paging() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        panel.setOpaque(false); panel.add(state); panel.add(previous); panel.add(next);
        return panel;
    }

    private void load() {
        setBusy(true);
        SecurityAuditQuery query;
        try {
            query = new SecurityAuditQuery(value(userId), value(action), value(result),
                    time(from), time(to), page, PAGE_SIZE);
        } catch (RuntimeException invalid) {
            setBusy(false); state.setText("时间格式应为 2026-08-30T10:00"); return;
        }
        CompletableFuture<PageResult<SecurityAuditView>> response;
        try { response = users.searchSecurityAudits(query); }
        catch (RuntimeException failure) { response = CompletableFuture.failedFuture(failure); }
        response.whenComplete((resultPage, failure) -> onEdt(() -> finish(resultPage, failure)));
    }

    private void finish(PageResult<SecurityAuditView> resultPage, Throwable failure) {
        setBusy(false); model.setRowCount(0);
        if (failure != null || resultPage == null) {
            state.setText(UserErrorMessages.operation(failure, "审计记录加载失败，请重试")); return;
        }
        for (SecurityAuditView row : resultPage.items()) model.addRow(new Object[]{
                row.auditId(), row.actorUserId(), row.actionCode(), row.targetType(),
                row.targetId(), row.resultCode(), row.createdAt()});
        state.setText(resultPage.total() == 0 ? "未找到审计记录" : "共 " + resultPage.total() + " 条");
        previous.setEnabled(page > 0);
        next.setEnabled((long) (page + 1) * PAGE_SIZE < resultPage.total());
    }

    private void setBusy(boolean busy) {
        search.setEnabled(!busy); previous.setEnabled(!busy && page > 0);
        next.setEnabled(!busy); if (busy) state.setText("正在加载…");
    }
    private static JPanel labeled(String label, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(UiSpacing.SPACE_1, 0));
        panel.setOpaque(false); panel.add(new JLabel(label), BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER); return panel;
    }
    private static JTextField field(String name, String accessibleName) {
        JTextField field = new JTextField(12); field.setName(name);
        field.getAccessibleContext().setAccessibleName(accessibleName); return field;
    }
    private static JButton button(String text, String name) {
        JButton button = new JButton(text); button.setName(name);
        button.getAccessibleContext().setAccessibleName(text); return button;
    }
    private static String value(JTextField field) {
        return field.getText().isBlank() ? null : field.getText().strip();
    }
    private static LocalDateTime time(JTextField field) {
        return field.getText().isBlank() ? null : LocalDateTime.parse(field.getText().strip());
    }
    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run(); else SwingUtilities.invokeLater(action);
    }
}
