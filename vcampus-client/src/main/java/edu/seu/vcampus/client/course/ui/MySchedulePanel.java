package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.ScheduleItem;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;

/** Detail-template page with the approved full-width weekly grid. */
public final class MySchedulePanel extends AbstractCoursePanel {
    private static final String[] DAYS = {"星期一", "星期二", "星期三", "星期四", "星期五"};
    private final JPanel grid = new JPanel(new GridBagLayout());
    private final CourseUiGateway gateway;

    public MySchedulePanel(CourseUiGateway gateway) {
        super("我的课表", "2026–2027 学年秋季学期 · 第 1–16 周");
        this.gateway = gateway;
        JPanel identity = new JPanel(new BorderLayout());
        identity.setBackground(UiColors.BACKGROUND_SUBTLE);
        identity.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(UiSpacing.MD, UiSpacing.LG, UiSpacing.MD, UiSpacing.LG)));
        identity.add(label("本科生课表", UiTypography.SECTION_TITLE, UiColors.TEXT_PRIMARY), BorderLayout.WEST);
        identity.add(label("更新时间：刚刚", UiTypography.CAPTION, UiColors.TEXT_SECONDARY), BorderLayout.EAST);
        body.add(identity, BorderLayout.NORTH);
        grid.setBackground(UiColors.BACKGROUND_PAGE);
        body.add(grid, BorderLayout.CENTER);
        refresh();
    }

    private void refresh() {
        long request = beginAsyncRequest();
        showState(ViewState.LOADING, "正在加载课表，请稍候");
        gateway.currentSchedule().whenComplete((items, error) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsAsyncResult(request)) return;
            if (error != null) {
                showState(ViewState.DISCONNECTED, "无法加载课表，请检查连接后重试");
                return;
            }
            render(items);
            showState(items.isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                    items.isEmpty() ? "本学期还没有课程安排" : "");
        }));
    }

    private void render(java.util.List<ScheduleItem> items) {
        grid.removeAll();
        Map<String, ScheduleItem> cells = new HashMap<>();
        for (ScheduleItem item : items) cells.put(item.dayOfWeek() + ":" + item.startPeriod(), item);
        addCell("节次", 0, 0, true);
        for (int day = 0; day < DAYS.length; day++) addCell(DAYS[day], day + 1, 0, true);
        String[] codes = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"};
        for (int period = 1; period <= 6; period++) {
            addCell("第 " + period + " 节", 0, period, true);
            for (int day = 0; day < DAYS.length; day++) {
                ScheduleItem item = cells.get(codes[day] + ":" + period);
                addCell(item == null ? "" : "<html><b>" + item.courseName() + "</b><br>" + item.className() + " · " + item.classroom() + "</html>", day + 1, period, false);
            }
        }
        revalidate(); repaint();
    }

    private void addCell(String text, int x, int y, boolean header) {
        JLabel cell = new JLabel(text, SwingConstants.CENTER);
        cell.setOpaque(true);
        cell.setFont(header ? UiTypography.BODY_BOLD : UiTypography.BODY);
        cell.setForeground(UiColors.TEXT_PRIMARY);
        cell.setBackground(header ? UiColors.BACKGROUND_SUBTLE : UiColors.BACKGROUND_PAGE);
        cell.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, UiColors.BORDER_DEFAULT));
        GridBagConstraints c = new GridBagConstraints(x, y, 1, 1, x == 0 ? .35 : 1, 1,
                GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
        grid.add(cell, c);
    }
}
