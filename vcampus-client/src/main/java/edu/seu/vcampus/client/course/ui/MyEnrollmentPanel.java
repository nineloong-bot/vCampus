package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.TermPhaseView;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Window;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

@FunctionalInterface
interface DropConfirmation {
    boolean confirm(Window owner, String courseLabel);
}

/** Query-list page backed by COURSE_GET_MY_ENROLLMENTS. */
public final class MyEnrollmentPanel extends AbstractCoursePanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final CourseUiGateway gateway;
    private final DropConfirmation confirmation;
    private final Runnable onEnrollmentChanged;
    private final JLabel summary = label("共 0 条", UiTypography.BODY, UiColors.TEXT_SECONDARY);
    private final JLabel phaseSummary = label("正在读取服务端阶段…", UiTypography.BODY, UiColors.TEXT_PRIMARY);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"教学班编号", "选课类型", "状态", "选课时间", "记录版本"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = table(new Object[0][0], new Object[0]);
    private final JButton drop = secondary("退选所选课程");
    private final List<EnrollmentView> enrollments = new ArrayList<>();
    private boolean dropPhaseOpen;
    private boolean dropPending;

    public MyEnrollmentPanel(CourseUiGateway gateway) {
        this(gateway, (owner, courseLabel) -> JOptionPane.showConfirmDialog(
                        owner, "确认退选“" + courseLabel + "”吗？", "确认退选",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION,
                () -> { });
    }

    MyEnrollmentPanel(CourseUiGateway gateway, DropConfirmation confirmation, Runnable onEnrollmentChanged) {
        super("我的选课", "查看当前学期已选教学班、选课类型和记录状态。");
        this.gateway = gateway;
        this.confirmation = confirmation;
        this.onEnrollmentChanged = onEnrollmentChanged;
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(UiColors.BACKGROUND_SUBTLE);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG)));
        toolbar.add(phaseSummary, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        drop.setEnabled(false);
        drop.addActionListener(event -> dropSelected());
        actions.add(drop);
        JButton refresh = primary("刷新选课");
        refresh.addActionListener(event -> refresh());
        actions.add(refresh);
        toolbar.add(actions, BorderLayout.EAST);
        body.add(toolbar, BorderLayout.NORTH);

        table.setModel(model);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        table.getAccessibleContext().setAccessibleName("我的选课记录");
        table.getSelectionModel().addListSelectionListener(event -> updateDropEnabled());
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
        dropPending = false;
        dropPhaseOpen = false;
        updateDropEnabled();
        showState(ViewState.LOADING, "正在加载我的选课，请稍候");
        var enrollmentRequest = gateway.currentEnrollments();
        var phaseRequest = gateway.currentTermId().thenCompose(gateway::getTermPhase);
        enrollmentRequest.thenCombine(phaseRequest, Data::new).whenComplete((data, error) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsAsyncResult(request)) return;
            if (error != null) {
                dropPhaseOpen = false;
                updateDropEnabled();
                showState(ViewState.DISCONNECTED, "无法加载我的选课，请检查连接后重试");
                return;
            }
            model.setRowCount(0);
            enrollments.clear();
            enrollments.addAll(data.enrollments());
            for (EnrollmentView enrollment : enrollments) {
                model.addRow(new Object[]{enrollment.offeringId(), typeName(enrollment.enrollmentType()),
                        statusName(enrollment.enrollmentStatus()), TIME.format(enrollment.enrolledAt()),
                        "v" + enrollment.rowVersion()});
            }
            summary.setText("共 " + enrollments.size() + " 条");
            phaseSummary.setText(phaseText(data.phase()));
            dropPhaseOpen = allowsDrop(data.phase());
            updateDropEnabled();
            showState(enrollments.isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                    enrollments.isEmpty() ? "当前学期还没有选课，可前往“教学班查询”选择课程" : "");
        }));
    }

    @Override protected void refreshAfterNavigation() { refresh(); }

    private static String typeName(String type) {
        return "RETAKE".equals(type) ? "重修" : "NORMAL".equals(type) ? "正常选课" : type;
    }

    private static String statusName(String status) {
        return "ACTIVE".equals(status) ? "有效" : "DROPPED".equals(status) ? "已退选" : status;
    }

    private void dropSelected() {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            showState(ViewState.ERROR, "请先选择要退选的课程");
            return;
        }
        EnrollmentView enrollment = enrollments.get(table.convertRowIndexToModel(selected));
        if (!"ACTIVE".equals(enrollment.enrollmentStatus())) {
            showState(ViewState.ERROR, "该选课记录已退选，请刷新后重试");
            return;
        }
        if (!confirmation.confirm(SwingUtilities.getWindowAncestor(this), labelFor(enrollment))) return;
        submitDrop(new DropCommand(enrollment.enrollmentId(), enrollment.rowVersion()));
    }

    private void submitDrop(DropCommand command) {
        long request = beginAsyncRequest();
        dropPending = true;
        updateDropEnabled();
        showState(ViewState.SUBMITTING, "正在退选，请勿重复操作");
        gateway.drop(command).whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsAsyncResult(request)) return;
            dropPending = false;
            updateDropEnabled();
            if (error == null) {
                onEnrollmentChanged.run();
                refresh();
                return;
            }
            Throwable cause = error;
            while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
            showState(ViewState.ERROR,
                    cause.getMessage() == null ? "退选失败，请刷新后重试" : cause.getMessage());
        }));
    }

    private void updateDropEnabled() {
        int selected = table.getSelectedRow();
        boolean active = false;
        if (selected >= 0) {
            int modelRow = table.convertRowIndexToModel(selected);
            active = modelRow < enrollments.size()
                    && "ACTIVE".equals(enrollments.get(modelRow).enrollmentStatus());
        }
        drop.setEnabled(!dropPending && dropPhaseOpen && active);
    }

    private static boolean allowsDrop(TermPhaseView phase) {
        return !"CLOSED".equals(phase.termStatus())
                && ("ENROLLMENT".equals(phase.phase()) || "ADJUSTMENT".equals(phase.phase()));
    }

    private static String phaseText(TermPhaseView phase) {
        String state = switch (phase.phase()) {
            case "ENROLLMENT" -> "正常选课开放";
            case "ADJUSTMENT" -> "退改补开放";
            default -> "只读阶段";
        };
        if ("CLOSED".equals(phase.termStatus())) state = "学期已关闭";
        return "服务端阶段：" + state + "    选课时间窗：" + TIME.format(phase.enrollmentStartAt())
                + " 至 " + TIME.format(phase.enrollmentEndAt()) + "    退改补时间窗："
                + TIME.format(phase.adjustmentStartAt()) + " 至 " + TIME.format(phase.adjustmentEndAt());
    }

    private static String labelFor(EnrollmentView enrollment) {
        return enrollment.offeringId();
    }

    private record Data(List<EnrollmentView> enrollments, TermPhaseView phase) { }
}
