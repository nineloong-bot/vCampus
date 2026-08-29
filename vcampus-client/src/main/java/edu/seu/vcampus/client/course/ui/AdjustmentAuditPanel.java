package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.AdjustmentAuditQuery;
import edu.seu.vcampus.common.course.AdjustmentAuditView;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Administrator query page for live add/drop/change audit records. */
public final class AdjustmentAuditPanel extends AbstractCoursePanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final CourseUiGateway gateway;
    private final JTextField student = field("学生编号");
    private final JTextField term = field("学期编号");
    private final JComboBox<String> type = combo("操作类型", "全部操作", "补选", "退选", "改选");
    private final JComboBox<String> result = combo("操作结果", "全部结果", "成功", "失败");
    private final DefaultTableModel model = readOnlyModel("操作时间", "学生", "操作", "原教学班", "目标教学班", "结果", "失败原因");
    private final CoursePager pager;

    public AdjustmentAuditPanel(CourseUiGateway gateway) {
        super("选课调整审计", "查询补选、退选、改选及失败原因；记录由服务端生成且不可修改。");
        this.gateway = gateway;
        this.pager = new CoursePager(50, this::search);
        body.add(filters(), BorderLayout.NORTH);
        JTable table = table(new Object[0][0], new Object[0]);
        table.setModel(model);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        table.getAccessibleContext().setAccessibleName("选课调整审计记录");
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        JPanel listing = new JPanel(new BorderLayout(0, UiSpacing.MD));
        listing.setOpaque(false);
        listing.add(scroll, BorderLayout.CENTER);
        listing.add(pager, BorderLayout.SOUTH);
        body.add(listing, BorderLayout.CENTER);
        search(0);
    }

    private JPanel filters() {
        JPanel panel = new JPanel();
        panel.setBackground(UiColors.BACKGROUND_SUBTLE);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG));
        panel.add(label("学生编号", UiTypography.CAPTION, UiColors.TEXT_PRIMARY));
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(student);
        panel.add(Box.createHorizontalStrut(UiSpacing.MD));
        panel.add(label("学期编号", UiTypography.CAPTION, UiColors.TEXT_PRIMARY));
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(term);
        panel.add(Box.createHorizontalStrut(UiSpacing.MD));
        panel.add(label("操作类型", UiTypography.CAPTION, UiColors.TEXT_PRIMARY));
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(type);
        panel.add(Box.createHorizontalStrut(UiSpacing.MD));
        panel.add(label("操作结果", UiTypography.CAPTION, UiColors.TEXT_PRIMARY));
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(result);
        panel.add(Box.createHorizontalGlue());
        JButton reset = secondary("重置条件");
        reset.addActionListener(event -> {
            student.setText("");
            term.setText("");
            type.setSelectedIndex(0);
            result.setSelectedIndex(0);
            search(0);
        });
        panel.add(reset);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        JButton search = primary("查询审计记录");
        search.addActionListener(event -> search(0));
        panel.add(search);
        return panel;
    }

    private void search(int pageNumber) {
        String studentId = blankToNull(student.getText());
        String termId = blankToNull(term.getText());
        if (tooLongId(studentId) || tooLongId(termId)) {
            showState(ViewState.ERROR, "学生编号和学期编号均不能超过 36 个字符");
            return;
        }
        long request = beginAsyncRequest();
        showState(ViewState.LOADING, "正在查询审计记录，请稍候");
        AdjustmentAuditQuery query = new AdjustmentAuditQuery(studentId, termId,
                selected(type, new String[]{null, "ADD", "DROP", "CHANGE"}),
                selected(result, new String[]{null, "SUCCEEDED", "FAILED"}), pageNumber, 50);
        gateway.searchAdjustmentAudits(query).whenComplete((page, error) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsAsyncResult(request)) return;
            if (error != null) {
                showState(ViewState.DISCONNECTED, "无法读取审计记录，请检查连接后重试");
                return;
            }
            model.setRowCount(0);
            for (AdjustmentAuditView row : page.items()) model.addRow(new Object[]{
                    TIME.format(row.operatedAt()), row.studentId(), adjustmentType(row.adjustmentType()),
                    textOrDash(row.sourceOfferingId()), textOrDash(row.targetOfferingId()),
                    operationResult(row.operationResult()), failureReason(row.failureCode())});
            pager.showPage(page.page(), page.total());
            showState(page.items().isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                    page.items().isEmpty() ? "未找到符合条件的审计记录，请调整筛选条件" : "");
        }));
    }

    @Override protected void refreshAfterNavigation() { search(pager.currentPage()); }

    private static boolean tooLongId(String value) { return value != null && value.length() > 36; }

    private static JTextField field(String name) {
        JTextField field = new JTextField();
        field.setPreferredSize(new Dimension(110, UiDimensions.CONTROL_HEIGHT));
        field.setMaximumSize(new Dimension(130, UiDimensions.CONTROL_HEIGHT));
        field.setFont(UiTypography.BODY);
        field.getAccessibleContext().setAccessibleName(name);
        field.setToolTipText(name);
        return field;
    }

    private static JComboBox<String> combo(String accessibleName, String... values) {
        JComboBox<String> combo = new JComboBox<>(values);
        combo.setFont(UiTypography.BODY);
        combo.setPreferredSize(new Dimension(100, UiDimensions.CONTROL_HEIGHT));
        combo.setMaximumSize(new Dimension(110, UiDimensions.CONTROL_HEIGHT));
        combo.getAccessibleContext().setAccessibleName(accessibleName);
        return combo;
    }

    private static String selected(JComboBox<String> combo, String[] values) { return values[combo.getSelectedIndex()]; }
    private static String blankToNull(String value) { String clean = value.trim(); return clean.isEmpty() ? null : clean; }
    private static String textOrDash(String value) { return value == null || value.isBlank() ? "—" : value; }
    private static String adjustmentType(String value) {
        return switch (value) { case "ADD" -> "补选"; case "DROP" -> "退选"; case "CHANGE" -> "改选"; default -> "其他"; };
    }
    private static String operationResult(String value) { return "SUCCEEDED".equals(value) ? "成功" : "失败"; }
    private static String failureReason(String value) {
        if (value == null || value.isBlank()) return "—";
        return switch (value) {
            case "COURSE_OFFERING_FULL" -> "教学班容量已满";
            case "COURSE_SCHEDULE_CONFLICT" -> "上课时间冲突";
            case "COURSE_ADJUSTMENT_NOT_OPEN" -> "当前不在调整开放期";
            case "COMMON_CONCURRENT_MODIFICATION" -> "记录已被其他操作修改";
            default -> "业务规则校验未通过";
        };
    }

    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) { public boolean isCellEditable(int row, int column) { return false; } };
    }
}
