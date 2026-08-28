package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/** Administrator offering list backed by live offering search. */
public final class OfferingManagementPanel extends AbstractCoursePanel {
    private final CourseUiGateway gateway;
    private final JTextField termId = field("学期编号", "2026-autumn");
    private final JTextField keyword = field("课程或教学班", "");
    private final DefaultTableModel model = readOnlyModel(
            "课程代码", "课程名称", "教学班", "授课教师", "容量", "已选", "状态", "版本");
    private final JTable table = table(new Object[0][0], new Object[0]);
    private final List<OfferingSummary> offerings = new ArrayList<>();

    public OfferingManagementPanel(CourseUiGateway gateway) {
        super("教学班管理", "维护教学班容量、教师、上课时间地点与开放状态。");
        this.gateway = gateway;
        body.add(filters(), BorderLayout.NORTH);
        table.setModel(model);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        table.getAccessibleContext().setAccessibleName("教学班管理列表");
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        body.add(scroll, BorderLayout.CENTER);
        body.add(actions(), BorderLayout.SOUTH);
        search();
    }

    private JPanel actions() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_DEFAULT));
        panel.add(label("保存时整体校验并更新教学班及全部上课安排", UiTypography.CAPTION, UiColors.TEXT_SECONDARY));
        panel.add(Box.createHorizontalGlue());
        JButton edit = secondary("编辑所选");
        edit.addActionListener(event -> editSelected());
        panel.add(edit);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        JButton create = primary("新建教学班");
        create.addActionListener(event -> openEditor(null));
        panel.add(create);
        return panel;
    }

    private JPanel filters() {
        JPanel panel = new JPanel();
        panel.setBackground(UiColors.BACKGROUND_SUBTLE);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG));
        panel.add(label("学期编号", UiTypography.BODY, UiColors.TEXT_PRIMARY));
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(termId);
        panel.add(Box.createHorizontalStrut(UiSpacing.MD));
        panel.add(label("课程或教学班", UiTypography.BODY, UiColors.TEXT_PRIMARY));
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(keyword);
        panel.add(Box.createHorizontalGlue());
        JButton reset = secondary("重置条件");
        reset.addActionListener(event -> { termId.setText("2026-autumn"); keyword.setText(""); search(); });
        panel.add(reset);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        JButton search = primary("查询教学班");
        search.addActionListener(event -> search());
        panel.add(search);
        return panel;
    }

    private void search() {
        String term = termId.getText().trim();
        if (term.isEmpty()) { showState(ViewState.ERROR, "请输入学期编号后再查询"); return; }
        showState(ViewState.LOADING, "正在加载教学班，请稍候");
        gateway.searchOfferings(new OfferingSearchQuery(term, keyword.getText().trim(), null, false, 0, 50))
                .whenComplete((page, error) -> SwingUtilities.invokeLater(() -> {
                    model.setRowCount(0);
                    offerings.clear();
                    if (error != null) { showState(ViewState.DISCONNECTED, "无法加载教学班，请检查连接后重试"); return; }
                    offerings.addAll(page.items());
                    for (OfferingSummary row : page.items()) model.addRow(new Object[]{
                            row.courseCode(), row.courseName(), row.className(), row.teacherUserId(), row.capacity(),
                            row.enrolledCount(), status(row.offeringStatus()), "v" + row.rowVersion()});
                    showState(page.items().isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                            page.items().isEmpty() ? "当前学期没有符合条件的教学班" : "");
                }));
    }

    private void editSelected() {
        int selected = table.getSelectedRow();
        if (selected < 0) { showState(ViewState.ERROR, "请先选择要编辑的教学班"); return; }
        openEditor(offerings.get(table.convertRowIndexToModel(selected)));
    }

    private void openEditor(OfferingSummary offering) {
        new OfferingEditorDialog(SwingUtilities.getWindowAncestor(this), gateway, offering, this::search).setVisible(true);
    }

    private static JTextField field(String name, String value) {
        JTextField field = new JTextField(value);
        field.setFont(UiTypography.BODY);
        field.setPreferredSize(new Dimension(220, UiDimensions.CONTROL_HEIGHT));
        field.setMaximumSize(new Dimension(300, UiDimensions.CONTROL_HEIGHT));
        field.getAccessibleContext().setAccessibleName(name);
        field.setToolTipText(name);
        return field;
    }

    private static String status(String value) {
        return switch (value) { case "DRAFT" -> "草稿"; case "OPEN" -> "开放"; case "CLOSED" -> "已关闭"; case "CANCELLED" -> "已取消"; default -> "未知"; };
    }
    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) { public boolean isCellEditable(int row, int column) { return false; } };
    }
}
