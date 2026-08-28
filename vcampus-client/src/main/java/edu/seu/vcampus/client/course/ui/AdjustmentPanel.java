package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/** Adjustment-window page for live add, drop, and atomic change commands. */
public final class AdjustmentPanel extends AbstractCoursePanel {
    private final CourseUiGateway gateway;
    private final DefaultTableModel enrollmentModel = readOnlyModel("课程", "教学班", "类型", "状态", "版本");
    private final DefaultTableModel offeringModel = readOnlyModel("课程代码", "课程名称", "教学班", "余量", "状态");
    private final JTable enrollmentTable = table(new Object[0][0], new Object[0]);
    private final JTable offeringTable = table(new Object[0][0], new Object[0]);
    private final List<EnrollmentView> enrollments = new ArrayList<>();
    private final List<OfferingSummary> offerings = new ArrayList<>();

    public AdjustmentPanel(CourseUiGateway gateway) {
        super("选课调整", "调整开放期内可补选、退选或原子改选；失败不会影响原选课。");
        this.gateway = gateway;
        enrollmentTable.setModel(enrollmentModel);
        enrollmentTable.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        enrollmentTable.getAccessibleContext().setAccessibleName("当前选课记录");
        offeringTable.setModel(offeringModel);
        offeringTable.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        offeringTable.getAccessibleContext().setAccessibleName("可调整教学班");

        JPanel tables = new JPanel(new GridLayout(2, 1, 0, UiSpacing.LG));
        tables.setOpaque(false);
        tables.add(section("当前选课：选择需要退选或改选的记录", enrollmentTable));
        tables.add(section("目标教学班：选择补选或改选目标", offeringTable));
        body.add(tables, BorderLayout.CENTER);
        body.add(actions(), BorderLayout.SOUTH);
        refresh();
    }

    private JPanel actions() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_DEFAULT));
        panel.add(label("改选将同时锁定原教学班和目标教学班", UiTypography.CAPTION, UiColors.TEXT_SECONDARY));
        panel.add(Box.createHorizontalGlue());
        JButton add = secondary("补选所选");
        add.addActionListener(event -> lateAdd(add));
        panel.add(add);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        JButton drop = secondary("退选所选");
        drop.addActionListener(event -> drop(drop));
        panel.add(drop);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        JButton change = primary("确认改选");
        change.addActionListener(event -> change(change));
        panel.add(change);
        return panel;
    }

    private static JPanel section(String title, JTable table) {
        JPanel panel = new JPanel(new BorderLayout(0, UiSpacing.SM));
        panel.setOpaque(false);
        panel.add(label(title, UiTypography.SECTION_TITLE, UiColors.TEXT_PRIMARY), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void refresh() {
        showState(ViewState.LOADING, "正在加载调整数据，请稍候");
        var enrollmentRequest = gateway.currentEnrollments();
        var offeringRequest = gateway.searchOfferings(new OfferingSearchQuery("2026-autumn", "", null, true, 0, 100));
        enrollmentRequest.thenCombine(offeringRequest, Data::new).whenComplete((data, error) ->
                SwingUtilities.invokeLater(() -> {
                    enrollmentModel.setRowCount(0);
                    offeringModel.setRowCount(0);
                    enrollments.clear();
                    offerings.clear();
                    if (error != null) {
                        showState(ViewState.DISCONNECTED, "无法加载调整数据，请检查连接后重试");
                        return;
                    }
                    enrollments.addAll(data.enrollments());
                    offerings.addAll(data.offerings().items());
                    for (EnrollmentView row : enrollments) {
                        OfferingSummary offering = findOffering(row.offeringId());
                        enrollmentModel.addRow(new Object[]{
                                offering == null ? "课程信息待同步" : offering.courseName(),
                                offering == null ? "教学班信息待同步" : offering.className(),
                                enrollmentType(row.enrollmentType()), enrollmentStatus(row.enrollmentStatus()),
                                "v" + row.rowVersion()});
                    }
                    for (OfferingSummary row : offerings) offeringModel.addRow(new Object[]{
                            row.courseCode(), row.courseName(), row.className(),
                            Math.max(0, row.capacity() - row.enrolledCount()) + " / " + row.capacity(), "可调整"});
                    showState(ViewState.NORMAL, "");
                }));
    }

    private OfferingSummary findOffering(String offeringId) {
        return offerings.stream().filter(row -> row.offeringId().equals(offeringId)).findFirst().orElse(null);
    }

    private static String enrollmentType(String type) {
        return switch (type) {
            case "NORMAL" -> "正常选课";
            case "LATE_ADD" -> "补选";
            case "RETAKE" -> "重修";
            default -> "其他";
        };
    }

    private static String enrollmentStatus(String status) {
        return switch (status) {
            case "ACTIVE" -> "有效";
            case "DROPPED" -> "已退选";
            default -> "未知";
        };
    }

    private void lateAdd(JButton button) {
        int target = offeringTable.getSelectedRow();
        if (target < 0) { showState(ViewState.ERROR, "请先选择要补选的教学班"); return; }
        submit(button, "正在补选…", gateway.lateAdd(new LateAddCommand(offerings.get(target).offeringId())), "补选成功，可在我的选课查看");
    }

    private void drop(JButton button) {
        int source = enrollmentTable.getSelectedRow();
        if (source < 0) { showState(ViewState.ERROR, "请先选择要退选的记录"); return; }
        EnrollmentView selected = enrollments.get(source);
        submit(button, "正在退选…", gateway.drop(new DropCommand(selected.enrollmentId(), selected.rowVersion())), "退选成功，课表已更新");
    }

    private void change(JButton button) {
        int source = enrollmentTable.getSelectedRow();
        int target = offeringTable.getSelectedRow();
        if (source < 0 || target < 0) { showState(ViewState.ERROR, "请同时选择原选课记录和目标教学班"); return; }
        EnrollmentView selected = enrollments.get(source);
        OfferingSummary targetOffering = offerings.get(target);
        submit(button, "正在改选…", gateway.change(new ChangeOfferingCommand(
                selected.enrollmentId(), targetOffering.offeringId(), selected.rowVersion())), "改选成功，原选课已安全替换");
    }

    private void submit(JButton button, String busyText, java.util.concurrent.CompletableFuture<?> request, String success) {
        String idleText = button.getText();
        button.setEnabled(false);
        button.setText(busyText);
        showState(ViewState.SUBMITTING, busyText + " 请勿重复操作");
        request.whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
            button.setEnabled(true);
            button.setText(idleText);
            if (error == null) { showState(ViewState.NORMAL, success); return; }
            Throwable cause = error;
            while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof edu.seu.vcampus.client.course.service.CourseClientException failure
                    && "COMMON_CONCURRENT_MODIFICATION".equals(failure.code())) {
                showState(ViewState.CONFLICT, "记录已被其他操作修改，请刷新数据后重试");
            } else {
                showState(ViewState.ERROR, cause.getMessage() == null ? "调整失败，请刷新后重试" : cause.getMessage());
            }
        }));
    }

    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private record Data(List<EnrollmentView> enrollments, edu.seu.vcampus.common.paging.PageResult<OfferingSummary> offerings) { }
}
