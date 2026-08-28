package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.EnrollmentView;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/** Query-list page backed by COURSE_GET_MY_ENROLLMENTS. */
public final class MyEnrollmentPanel extends AbstractCoursePanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final CourseUiGateway gateway;
    private final JLabel summary = label("共 0 条", UiTypography.BODY, UiColors.TEXT_SECONDARY);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"教学班编号", "选课类型", "状态", "选课时间", "记录版本"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final AtomicLong requestSequence = new AtomicLong();

    public MyEnrollmentPanel(CourseUiGateway gateway) {
        super("我的选课", "查看当前学期已选教学班、选课类型和记录状态。");
        this.gateway = gateway;
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(UiColors.BACKGROUND_SUBTLE);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG)));
        toolbar.add(label("当前学期的有效及历史选课记录", UiTypography.BODY, UiColors.TEXT_PRIMARY), BorderLayout.WEST);
        JButton refresh = primary("刷新选课");
        refresh.addActionListener(event -> refresh());
        toolbar.add(refresh, BorderLayout.EAST);
        body.add(toolbar, BorderLayout.NORTH);

        JTable table = table(new Object[0][0], new Object[0]);
        table.setModel(model);
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
        long request = requestSequence.incrementAndGet();
        showState(ViewState.LOADING, "正在加载我的选课，请稍候");
        gateway.currentEnrollments().whenComplete((enrollments, error) -> SwingUtilities.invokeLater(() -> {
            if (request != requestSequence.get()) return;
            model.setRowCount(0);
            if (error != null) {
                showState(ViewState.DISCONNECTED, "无法加载我的选课，请检查连接后重试");
                return;
            }
            for (EnrollmentView enrollment : enrollments) {
                model.addRow(new Object[]{enrollment.offeringId(), typeName(enrollment.enrollmentType()),
                        statusName(enrollment.enrollmentStatus()), TIME.format(enrollment.enrolledAt()),
                        "v" + enrollment.rowVersion()});
            }
            summary.setText("共 " + enrollments.size() + " 条");
            showState(enrollments.isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                    enrollments.isEmpty() ? "当前学期还没有选课，可前往“教学班查询”选择课程" : "");
        }));
    }

    private static String typeName(String type) {
        return "RETAKE".equals(type) ? "重修" : "NORMAL".equals(type) ? "正常选课" : type;
    }

    private static String statusName(String status) {
        return "ACTIVE".equals(status) ? "有效" : "DROPPED".equals(status) ? "已退选" : status;
    }
}
