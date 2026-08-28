package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.RetakeCommand;

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
import java.util.ArrayList;
import java.util.List;

/** Failed-course retake workflow backed by eligibility and enrollment commands. */
public final class RetakePanel extends AbstractCoursePanel {
    private final CourseUiGateway gateway;
    private final JLabel summary = label("共 0 个可选教学班", UiTypography.BODY, UiColors.TEXT_SECONDARY);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"课程代码", "课程名称", "教学班", "授课教师", "余量", "资格状态"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = table(new Object[0][0], new Object[0]);
    private final List<OfferingSummary> offerings = new ArrayList<>();
    private final JButton check = secondary("检查重修资格");
    private final JButton enroll = primary("确认重修");
    private int eligibleRow = -1;

    public RetakePanel(CourseUiGateway gateway) {
        super("重修选课", "选择教学班后先检查历史未通过记录，资格通过后才能提交重修。");
        this.gateway = gateway;
        table.setModel(model);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        table.getAccessibleContext().setAccessibleName("重修教学班列表");
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) { eligibleRow = -1; enroll.setEnabled(false); }
        });
        JPanel listing = new JPanel(new BorderLayout(0, UiSpacing.MD));
        listing.setOpaque(false);
        listing.add(summary, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        listing.add(scroll, BorderLayout.CENTER);
        body.add(listing, BorderLayout.CENTER);
        body.add(actions(), BorderLayout.SOUTH);
        loadOfferings();
    }

    private JPanel actions() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_DEFAULT));
        panel.add(label("只有历史结果为“未通过”的课程可以重修", UiTypography.CAPTION, UiColors.TEXT_SECONDARY));
        panel.add(Box.createHorizontalGlue());
        check.addActionListener(event -> checkEligibility());
        panel.add(check);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        enroll.setEnabled(false);
        enroll.addActionListener(event -> submitRetake());
        panel.add(enroll);
        return panel;
    }

    private void loadOfferings() {
        long request = beginAsyncRequest();
        showState(ViewState.LOADING, "正在加载重修教学班，请稍候");
        gateway.currentTermId().thenCompose(term -> gateway.searchOfferings(
                        new OfferingSearchQuery(term, "", null, true, 0, 100)))
                .whenComplete((page, error) -> SwingUtilities.invokeLater(() -> {
                    if (!acceptsAsyncResult(request)) return;
                    model.setRowCount(0);
                    offerings.clear();
                    if (error != null) { showState(ViewState.DISCONNECTED, "无法加载重修教学班，请检查连接后重试"); return; }
                    offerings.addAll(page.items());
                    for (OfferingSummary row : offerings) model.addRow(new Object[]{
                            row.courseCode(), row.courseName(), row.className(), row.teacherUserId(),
                            Math.max(0, row.capacity() - row.enrolledCount()) + " / " + row.capacity(), "待检查"});
                    summary.setText("共 " + page.total() + " 个可选教学班");
                    showState(offerings.isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                            offerings.isEmpty() ? "当前没有可用于重修的教学班" : "");
                }));
    }

    private void checkEligibility() {
        int row = table.getSelectedRow();
        if (row < 0) { showState(ViewState.ERROR, "请先选择一个重修教学班"); return; }
        OfferingSummary selected = offerings.get(table.convertRowIndexToModel(row));
        check.setEnabled(false);
        check.setText("正在检查…");
        long request = beginAsyncRequest();
        showState(ViewState.LOADING, "正在检查重修资格，请稍候");
        gateway.checkRetake(selected.courseId()).whenComplete((eligibility, error) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsAsyncResult(request)) return;
            check.setEnabled(true);
            check.setText("检查重修资格");
            if (error != null) { showState(ViewState.ERROR, "资格检查失败，请稍后重试"); return; }
            if (!eligibility.eligible()) {
                eligibleRow = -1;
                enroll.setEnabled(false);
                model.setValueAt("不符合资格", row, 5);
                showState(ViewState.ERROR, "未找到该课程的未通过记录，暂不能重修");
                return;
            }
            eligibleRow = row;
            enroll.setEnabled(true);
            model.setValueAt("可重修", row, 5);
            showState(ViewState.NORMAL, "资格检查通过，可以提交重修选课");
        }));
    }

    private void submitRetake() {
        int row = table.getSelectedRow();
        if (row < 0 || row != eligibleRow) { showState(ViewState.ERROR, "教学班已变化，请重新检查重修资格"); return; }
        OfferingSummary selected = offerings.get(table.convertRowIndexToModel(row));
        enroll.setEnabled(false);
        enroll.setText("正在重修选课…");
        long request = beginAsyncRequest();
        showState(ViewState.SUBMITTING, "正在提交重修选课，请勿重复操作");
        gateway.enrollRetake(new RetakeCommand(selected.offeringId())).whenComplete((result, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (!acceptsAsyncResult(request)) return;
                    enroll.setText("确认重修");
                    if (error != null) { enroll.setEnabled(true); showState(ViewState.ERROR, "重修选课失败，请刷新后重试"); return; }
                    model.setValueAt("已重修选课", row, 5);
                    showState(ViewState.NORMAL, "重修选课成功，可在我的选课和课表查看");
                }));
    }
}
