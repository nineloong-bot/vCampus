package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.EnrollmentView;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
interface DropConfirmation {
    boolean confirm(java.awt.Window owner, String courseLabel);
}

/** Read-only current-term enrollment list; all selection mutations live on the unified selection page. */
public final class MyEnrollmentPanel extends AbstractCoursePanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final CourseUiGateway gateway;
    private final JLabel summary = label("共 0 条", UiTypography.BODY, UiColors.TEXT_SECONDARY);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"教学班编号", "选课类型", "状态", "选课时间", "记录版本"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = table(new Object[0][0], new Object[0]);
    private final List<EnrollmentView> enrollments = new ArrayList<>();

    public MyEnrollmentPanel(CourseUiGateway gateway) {
        this(gateway, (owner, courseLabel) -> false, () -> { });
    }

    MyEnrollmentPanel(CourseUiGateway gateway, DropConfirmation ignoredConfirmation,
                      Runnable ignoredOnEnrollmentChanged) {
        super("我的选课", "查看当前学期已选教学班；选课和退课请前往“选课”页操作。");
        this.gateway = gateway;
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(UiColors.BACKGROUND_SUBTLE);
        toolbar.setBorder(BorderFactory.createEmptyBorder(UiSpacing.MD, UiSpacing.LG,
                UiSpacing.MD, UiSpacing.LG));
        toolbar.add(label("此页面仅供查询，选退操作统一在“选课”页完成。",
                UiTypography.BODY, UiColors.TEXT_PRIMARY), BorderLayout.CENTER);
        JButton refresh = primary("刷新选课");
        refresh.addActionListener(event -> refresh());
        toolbar.add(refresh, BorderLayout.EAST);
        body.add(toolbar, BorderLayout.NORTH);

        table.setModel(model);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        table.getAccessibleContext().setAccessibleName("我的选课记录");
        JPanel listing = new JPanel(new BorderLayout(0, UiSpacing.MD));
        listing.setOpaque(false);
        listing.add(summary, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        listing.add(scroll, BorderLayout.CENTER);
        body.add(listing, BorderLayout.CENTER);
        refresh();
    }

    public void refresh() {
        long request = beginAsyncRequest();
        showState(ViewState.LOADING, "正在加载我的选课，请稍候");
        gateway.currentEnrollments().whenComplete((values, error) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsAsyncResult(request)) return;
            if (error != null) {
                showState(ViewState.DISCONNECTED, "无法加载我的选课，请检查连接后重试");
                return;
            }
            model.setRowCount(0);
            enrollments.clear();
            enrollments.addAll(values);
            for (EnrollmentView enrollment : enrollments) {
                model.addRow(new Object[]{enrollment.offeringId(), typeName(enrollment.enrollmentType()),
                        statusName(enrollment.enrollmentStatus()), TIME.format(enrollment.enrolledAt()),
                        "v" + enrollment.rowVersion()});
            }
            summary.setText("共 " + enrollments.size() + " 条");
            showState(enrollments.isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                    enrollments.isEmpty() ? "当前学期还没有选课，可前往“选课”选择课程" : "");
        }));
    }

    @Override protected void refreshAfterNavigation() { refresh(); }

    private static String typeName(String type) {
        return "RETAKE".equals(type) ? "重修" : "NORMAL".equals(type) ? "正常选课" : type;
    }

    private static String statusName(String status) {
        return "ACTIVE".equals(status) ? "有效" : "DROPPED".equals(status) ? "已退选" : status;
    }
}
