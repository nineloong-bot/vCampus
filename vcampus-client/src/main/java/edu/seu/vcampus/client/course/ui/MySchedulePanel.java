package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.course.TermView;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Detail-template page with the approved full-width weekly grid. */
public final class MySchedulePanel extends AbstractCoursePanel {
    private static final String[] DAY_CODES = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
            "FRIDAY", "SATURDAY", "SUNDAY"};
    private static final String[] DAY_NAMES = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
    private final JPanel grid = new JPanel(new GridBagLayout());
    private final CourseUiGateway gateway;
    private final JLabel termSummary = label("当前学期", UiTypography.BODY, UiColors.TEXT_SECONDARY);

    public MySchedulePanel(CourseUiGateway gateway) {
        super("我的课表", "按当前学期展示课程的星期、节次、周次和上课地点。");
        this.gateway = gateway;
        JPanel identity = new JPanel(new BorderLayout());
        identity.setBackground(UiColors.BACKGROUND_SUBTLE);
        identity.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(UiSpacing.MD, UiSpacing.LG, UiSpacing.MD, UiSpacing.LG)));
        identity.add(label("本科生课表", UiTypography.SECTION_TITLE, UiColors.TEXT_PRIMARY), BorderLayout.WEST);
        identity.add(termSummary, BorderLayout.CENTER);
        identity.add(label("更新时间：刚刚", UiTypography.CAPTION, UiColors.TEXT_SECONDARY), BorderLayout.EAST);
        body.add(identity, BorderLayout.NORTH);
        grid.setBackground(UiColors.BACKGROUND_PAGE);
        body.add(grid, BorderLayout.CENTER);
        refresh();
    }

    private void refresh() {
        long request = beginAsyncRequest();
        showState(ViewState.LOADING, "正在加载课表，请稍候");
        var context = gateway.currentTermId().thenCompose(termId -> gateway.listTerms()
                .thenApply(terms -> new TermContext(termId, terms)));
        gateway.currentSchedule().thenCombine(context, SchedulePayload::new)
                .whenComplete((payload, error) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsAsyncResult(request)) return;
            if (error != null) {
                showState(ViewState.DISCONNECTED, "无法加载课表，请检查连接后重试");
                return;
            }
            render(payload.items());
            updateTermSummary(payload.items(), payload.context());
            showState(payload.items().isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                    payload.items().isEmpty() ? "本学期还没有课程安排" : "");
        }));
    }

    @Override protected void refreshAfterNavigation() { refresh(); }

    private void render(List<ScheduleItem> items) {
        grid.removeAll();
        Map<CellKey, List<CellEntry>> cells = new LinkedHashMap<>();
        for (ScheduleItem item : items) {
            for (int period = item.startPeriod(); period <= item.endPeriod(); period++) {
                cells.computeIfAbsent(new CellKey(item.dayOfWeek(), period), ignored -> new ArrayList<>())
                        .add(new CellEntry(item, period > item.startPeriod()));
            }
        }
        addCell("节次", 0, 0, true);
        for (int day = 0; day < DAY_NAMES.length; day++) addCell(DAY_NAMES[day], day + 1, 0, true);
        int maximumPeriod = Math.max(6, items.stream().mapToInt(ScheduleItem::endPeriod).max().orElse(6));
        for (int period = 1; period <= maximumPeriod; period++) {
            addCell("第 " + period + " 节", 0, period, true);
            for (int day = 0; day < DAY_CODES.length; day++) {
                List<CellEntry> scheduled = cells.getOrDefault(new CellKey(DAY_CODES[day], period), List.of());
                addCell(cellText(scheduled), day + 1, period, false);
            }
        }
        grid.revalidate();
        grid.repaint();
    }

    private void updateTermSummary(List<ScheduleItem> items, TermContext context) {
        String name = context.terms().stream().filter(term -> context.termId().equals(term.termId()))
                .map(TermView::termName).findFirst().orElse("当前学期");
        if (items.isEmpty()) {
            termSummary.setText(name);
            return;
        }
        int firstWeek = items.stream().mapToInt(ScheduleItem::startWeek).min().orElse(1);
        int lastWeek = items.stream().mapToInt(ScheduleItem::endWeek).max().orElse(firstWeek);
        termSummary.setText(name + " · 第 " + firstWeek + "–" + lastWeek + " 周");
    }

    private static String cellText(List<CellEntry> entries) {
        if (entries.isEmpty()) return "";
        StringBuilder text = new StringBuilder("<html>");
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) text.append("<br><br>");
            CellEntry entry = entries.get(index);
            ScheduleItem item = entry.item();
            if (entry.continuation()) {
                text.append("<i>").append(html(item.courseName())).append("（续）</i><br>")
                        .append("第").append(item.startWeek()).append("–").append(item.endWeek()).append("周");
            } else {
                text.append("<b>").append(html(item.courseName())).append("</b><br>")
                        .append(html(item.className())).append(" · ").append(html(item.classroom())).append("<br>")
                        .append("第").append(item.startWeek()).append("–").append(item.endWeek()).append("周 · 第")
                        .append(item.startPeriod()).append("–").append(item.endPeriod()).append("节");
            }
        }
        return text.append("</html>").toString();
    }

    private static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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

    private record CellKey(String dayOfWeek, int startPeriod) { }
    private record CellEntry(ScheduleItem item, boolean continuation) { }
    private record TermContext(String termId, List<TermView> terms) { }
    private record SchedulePayload(List<ScheduleItem> items, TermContext context) { }
}
