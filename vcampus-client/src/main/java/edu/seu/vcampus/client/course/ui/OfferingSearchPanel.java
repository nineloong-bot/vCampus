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
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.concurrent.atomic.AtomicLong;

/** Query-list page for available teaching offerings. */
public final class OfferingSearchPanel extends AbstractCoursePanel {
    private final CourseUiGateway gateway;
    private final JTextField keyword = new JTextField(18);
    private final JComboBox<String> weekday = new JComboBox<>(new String[]{"全部星期", "星期一", "星期二", "星期三", "星期四", "星期五"});
    private final JLabel summary = label("共 0 条", UiTypography.BODY, UiColors.TEXT_SECONDARY);
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"课程代码", "课程名称", "教学班", "授课教师", "上课安排", "余量", "状态"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable results = table(new Object[0][0], new Object[0]);
    private final AtomicLong requestSequence = new AtomicLong();

    public OfferingSearchPanel(CourseUiGateway gateway) {
        super("教学班查询", "按学期、课程或上课时间筛选教学班，查看余量后完成选课。");
        this.gateway = gateway;
        results.setModel(model);
        results.setRowHeight(UiDimensions.TABLE_ROW_HEIGHT);
        results.setShowVerticalLines(false);
        results.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        results.getTableHeader().setFont(UiTypography.BODY_BOLD);
        results.getAccessibleContext().setAccessibleName("教学班查询结果");
        body.add(filters(), BorderLayout.NORTH);
        JPanel listing = new JPanel(new BorderLayout(0, UiSpacing.MD));
        listing.setOpaque(false);
        listing.add(summary, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(results);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        listing.add(scroll, BorderLayout.CENTER);
        listing.add(pager(), BorderLayout.SOUTH);
        body.add(listing, BorderLayout.CENTER);
        refresh();
    }

    private JPanel filters() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.MD, UiSpacing.LG));
        panel.setBackground(UiColors.BACKGROUND_SUBTLE);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.BORDER_DEFAULT));
        panel.add(label("课程关键词", UiTypography.BODY, UiColors.TEXT_PRIMARY));
        keyword.setPreferredSize(new Dimension(220, UiDimensions.CONTROL_HEIGHT));
        keyword.getAccessibleContext().setAccessibleName("课程关键词");
        panel.add(keyword);
        panel.add(label("上课日期", UiTypography.BODY, UiColors.TEXT_PRIMARY));
        weekday.setPreferredSize(new Dimension(128, UiDimensions.CONTROL_HEIGHT));
        weekday.getAccessibleContext().setAccessibleName("上课日期");
        panel.add(weekday);
        JButton query = primary("查询教学班");
        query.addActionListener(event -> refresh());
        panel.add(query);
        JButton reset = secondary("重置条件");
        reset.addActionListener(event -> { keyword.setText(""); weekday.setSelectedIndex(0); refresh(); });
        panel.add(reset);
        return panel;
    }

    private JPanel pager() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(label("每页 20 条", UiTypography.CAPTION, UiColors.TEXT_SECONDARY));
        panel.add(Box.createHorizontalGlue());
        panel.add(secondary("上一页"));
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(label("第 1 / 1 页", UiTypography.BODY, UiColors.TEXT_PRIMARY));
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(secondary("下一页"));
        return panel;
    }

    public void refresh() {
        long request = requestSequence.incrementAndGet();
        gateway.searchOfferings(new OfferingSearchQuery("2026-autumn", keyword.getText(), null, true, 0, 20))
                .whenComplete((page, error) -> SwingUtilities.invokeLater(() -> {
                    if (request != requestSequence.get()) return;
                    model.setRowCount(0);
                    if (error != null) { summary.setText("加载失败，请检查连接后重试"); return; }
                    for (OfferingSummary row : page.items()) {
                        String time = row.schedules().isEmpty() ? "待安排" : row.schedules().getFirst().dayOfWeek()
                                + " 第" + row.schedules().getFirst().startPeriod() + "–" + row.schedules().getFirst().endPeriod() + "节";
                        model.addRow(new Object[]{row.courseCode(), row.courseName(), row.className(), row.teacherUserId(), time,
                                (row.capacity() - row.enrolledCount()) + " / " + row.capacity(), "可选"});
                    }
                    summary.setText("共 " + page.total() + " 条");
                }));
    }
}
