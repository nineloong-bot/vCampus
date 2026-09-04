package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.common.course.TermView;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Administrator academic-term list; course-selection opening is managed separately. */
public final class TermManagementPanel extends AbstractCoursePanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final CourseUiGateway gateway;
    private final DefaultTableModel model = readOnlyModel("学期代码", "学期名称", "开学日期", "结束日期", "状态", "版本");
    private final JTable table = table(new Object[0][0], new Object[0]);
    private final List<TermView> terms = new ArrayList<>();

    public TermManagementPanel(CourseUiGateway gateway) {
        super("学期管理", "维护学期名称、教学日期和状态；选课开放请前往“选课阶段”。");
        this.gateway = gateway;
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SM, 0));
        toolbar.setOpaque(false);
        JButton refresh = secondary("刷新学期");
        refresh.addActionListener(event -> load());
        toolbar.add(refresh);
        JButton edit = secondary("编辑所选");
        edit.addActionListener(event -> editSelected());
        toolbar.add(edit);
        JButton create = primary("新建学期");
        create.addActionListener(event -> openEditor(null));
        toolbar.add(create);
        body.add(toolbar, BorderLayout.NORTH);
        table.setModel(model);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        table.getAccessibleContext().setAccessibleName("学期配置列表");
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        body.add(scroll, BorderLayout.CENTER);
        load();
    }

    private void load() {
        long request = beginAsyncRequest();
        showState(ViewState.LOADING, "正在加载学期配置，请稍候");
        gateway.listTerms().whenComplete((terms, error) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsAsyncResult(request)) return;
            if (error != null) { showState(ViewState.DISCONNECTED, "无法加载学期配置，请检查连接后重试"); return; }
            model.setRowCount(0);
            this.terms.clear();
            this.terms.addAll(terms);
            for (TermView term : terms) model.addRow(new Object[]{
                    term.termCode(), term.termName(), term.startDate(), term.endDate(), status(term.termStatus()), "v" + term.rowVersion()});
            showState(terms.isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                    terms.isEmpty() ? "当前尚未配置学期，请新建学期后继续" : "");
        }));
    }

    @Override protected void refreshAfterNavigation() { load(); }

    private void editSelected() {
        int selected = table.getSelectedRow();
        if (selected < 0) { showState(ViewState.ERROR, "请先选择要编辑的学期"); return; }
        openEditor(terms.get(table.convertRowIndexToModel(selected)));
    }

    private void openEditor(TermView term) {
        new TermEditorDialog(SwingUtilities.getWindowAncestor(this), gateway, term, this::load).setVisible(true);
    }

    private static String window(java.time.Instant start, java.time.Instant end) { return TIME.format(start) + " 至 " + TIME.format(end); }
    private static String status(String value) {
        return switch (value) { case "PLANNED" -> "未开始"; case "ACTIVE" -> "进行中"; case "CLOSED" -> "已关闭"; default -> "未知"; };
    }
    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) { public boolean isCellEditable(int row, int column) { return false; } };
    }
}
