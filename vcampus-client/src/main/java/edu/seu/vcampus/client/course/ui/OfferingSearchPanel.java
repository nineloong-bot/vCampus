package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.client.course.service.CourseClientException;

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
import java.util.ArrayList;
import java.util.List;

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
    private final List<OfferingSummary> visibleOfferings = new ArrayList<>();

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
        JPanel resultHeader = new JPanel(new BorderLayout());
        resultHeader.setOpaque(false);
        resultHeader.add(summary, BorderLayout.WEST);
        JButton enroll = primary("选择教学班");
        enroll.addActionListener(event -> enrollSelected(enroll));
        resultHeader.add(enroll, BorderLayout.EAST);
        listing.add(resultHeader, BorderLayout.NORTH);
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
        long request = beginAsyncRequest();
        showState(ViewState.LOADING, "正在加载教学班，请稍候");
        gateway.currentTermId().thenCompose(term -> gateway.searchOfferings(new OfferingSearchQuery(
                        term, keyword.getText(), selectedDay(), true, 0, 20)))
                .whenComplete((page, error) -> SwingUtilities.invokeLater(() -> {
                    if (!acceptsAsyncResult(request)) return;
                    model.setRowCount(0);
                    visibleOfferings.clear();
                    if (error != null) {
                        Throwable cause = error;
                        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                        if (cause instanceof CourseClientException failure
                                && ("COMMON_NETWORK_ERROR".equals(failure.code()) || "COMMON_TIMEOUT".equals(failure.code()))) {
                            summary.setText("共 0 条");
                            showState(ViewState.DISCONNECTED, "连接已断开，请检查网络后重试；已加载内容将继续保留");
                        } else if (cause instanceof CourseClientException failure
                                && "COMMON_CONCURRENT_MODIFICATION".equals(failure.code())) {
                            showState(ViewState.CONFLICT, "教学班信息已被修改，请刷新数据后重试");
                        } else {
                            showState(ViewState.ERROR, "加载教学班失败，请稍后重试或重置筛选条件");
                        }
                        return;
                    }
                    for (OfferingSummary row : page.items()) {
                        visibleOfferings.add(row);
                        String time = row.schedules().isEmpty() ? "待安排" : dayName(row.schedules().getFirst().dayOfWeek())
                                + " 第" + row.schedules().getFirst().startPeriod() + "–" + row.schedules().getFirst().endPeriod() + "节";
                        model.addRow(new Object[]{row.courseCode(), row.courseName(), row.className(), row.teacherUserId(), time,
                                (row.capacity() - row.enrolledCount()) + " / " + row.capacity(), "可选"});
                    }
                    summary.setText("共 " + page.total() + " 条");
                    showState(page.items().isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                            page.items().isEmpty() ? "未找到教学班，请调整筛选条件或重置查询" : "");
                }));
    }

    private String selectedDay() {
        return switch (weekday.getSelectedIndex()) {
            case 1 -> "MONDAY"; case 2 -> "TUESDAY"; case 3 -> "WEDNESDAY";
            case 4 -> "THURSDAY"; case 5 -> "FRIDAY"; default -> null;
        };
    }

    private void enrollSelected(JButton button) {
        int selected = results.getSelectedRow();
        if (selected < 0 || selected >= visibleOfferings.size()) {
            showState(ViewState.ERROR, "请先在结果表中选择一个教学班");
            return;
        }
        OfferingSummary offering = visibleOfferings.get(results.convertRowIndexToModel(selected));
        long request = beginAsyncRequest();
        button.setEnabled(false);
        button.setText("正在选课…");
        showState(ViewState.SUBMITTING, "正在提交选课，请勿重复操作");
        gateway.enroll(new EnrollCommand(offering.offeringId())).whenComplete((enrollment, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (!acceptsAsyncResult(request)) return;
                    button.setEnabled(true);
                    button.setText("选择教学班");
                    if (error == null) {
                        int remaining = Math.max(0, offering.capacity() - offering.enrolledCount() - 1);
                        model.setValueAt(remaining + " / " + offering.capacity(), selected, 5);
                        showState(ViewState.NORMAL, "课程已选上，可在“我的选课”和“我的课表”查看");
                        return;
                    }
                    Throwable cause = error;
                    while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                    if (cause instanceof CourseClientException failure) {
                        switch (failure.code()) {
                            case "COURSE_OFFERING_FULL" -> showState(ViewState.ERROR, "教学班容量已满，请选择其他教学班");
                            case "COURSE_SCHEDULE_CONFLICT" -> showState(ViewState.ERROR, "所选教学班与当前课表冲突，请调整选择");
                            case "COMMON_CONCURRENT_MODIFICATION" -> showState(ViewState.CONFLICT, "教学班信息已变化，请刷新后重试");
                            default -> showState(ViewState.ERROR, failure.getMessage());
                        }
                    } else {
                        showState(ViewState.DISCONNECTED, "选课请求未送达，请检查连接后重试");
                    }
                }));
    }

    private static String dayName(String day) {
        return switch (day) {
            case "MONDAY" -> "星期一";
            case "TUESDAY" -> "星期二";
            case "WEDNESDAY" -> "星期三";
            case "THURSDAY" -> "星期四";
            case "FRIDAY" -> "星期五";
            case "SATURDAY" -> "星期六";
            case "SUNDAY" -> "星期日";
            default -> day;
        };
    }
}
