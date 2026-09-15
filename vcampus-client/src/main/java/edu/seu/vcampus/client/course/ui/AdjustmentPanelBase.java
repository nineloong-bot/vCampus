package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

/** State, confirmation contract and action gating for the adjustment panel segments. */
abstract class AdjustmentPanelBase extends AbstractCoursePanel {
    /** Shows the change confirmation dialog and runs the request when accepted. */
    @FunctionalInterface
    interface ChangeConfirmation {
        void show(java.awt.Window owner, OfferingSummary source, OfferingSummary target,
                  String conflictResult, Supplier<CompletableFuture<?>> request,
                  Runnable onSuccess);
    }

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    final CourseUiGateway gateway;
    final ChangeConfirmation confirmation;
    final JLabel phaseSummary = label("正在读取服务端阶段…", UiTypography.BODY, UiColors.TEXT_PRIMARY);
    final DefaultTableModel enrollmentModel = readOnlyModel("课程", "教学班", "类型", "状态", "版本");
    final DefaultTableModel offeringModel = readOnlyModel("课程代码", "课程名称", "教学班", "余量", "状态");
    final JTable enrollmentTable = table(new Object[0][0], new Object[0]);
    final JTable offeringTable = table(new Object[0][0], new Object[0]);
    final List<EnrollmentView> enrollments = new ArrayList<>();
    final List<OfferingSummary> offerings = new ArrayList<>();
    final List<ScheduleItem> currentSchedule = new ArrayList<>();
    final JButton add = secondary("补选所选");
    final JButton drop = secondary("退选所选");
    final JButton change = primary("确认改选");
    boolean adjustmentOpen;
    boolean mutationPending;
    long mutationSequence;

    AdjustmentPanelBase(CourseUiGateway gateway) {
        this(gateway, (owner, source, target, conflict, request, onSuccess) ->
                new OfferingDetailDialog(owner, source, target, conflict, request, onSuccess).setVisible(true));
    }

    AdjustmentPanelBase(CourseUiGateway gateway, ChangeConfirmation confirmation) {
        super("选课调整", "调整开放期内可补选、退选或原子改选；失败不会影响原选课。");
        this.gateway = gateway;
        this.confirmation = confirmation;
        enrollmentTable.setModel(enrollmentModel);
        enrollmentTable.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        enrollmentTable.getAccessibleContext().setAccessibleName("当前选课记录");
        enrollmentTable.getSelectionModel().addListSelectionListener(event -> updateEnrollmentActions());
        offeringTable.setModel(offeringModel);
        offeringTable.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        offeringTable.getAccessibleContext().setAccessibleName("可调整教学班");
        offeringTable.getSelectionModel().addListSelectionListener(event -> updateEnrollmentActions());

        phaseSummary.setOpaque(true);
        phaseSummary.setBackground(UiColors.BACKGROUND_SUBTLE);
        phaseSummary.setBorder(BorderFactory.createEmptyBorder(UiSpacing.MD, UiSpacing.LG, UiSpacing.MD, UiSpacing.LG));
        initializePanel();
    }

    /** Completes construction once the sections, actions and refresh flow are available. */
    abstract void initializePanel();

    /** Reloads the adjustment data from the service. */
    abstract void refresh();

    @Override protected void refreshAfterNavigation() { refresh(); }

    @Override public void removeNotify() {
        mutationSequence++;
        mutationPending = false;
        super.removeNotify();
    }

    void setActionButtonsEnabled(boolean enabled) {
        if (enabled && !mutationPending) {
            updateEnrollmentActions();
        } else {
            add.setEnabled(false);
            drop.setEnabled(false);
            change.setEnabled(false);
        }
    }

    void updateEnrollmentActions() {
        add.setEnabled(adjustmentOpen && !mutationPending);
        if (mutationPending) {
            drop.setEnabled(false);
            change.setEnabled(false);
            return;
        }
        int selected = enrollmentTable.getSelectedRow();
        boolean active = false;
        if (selected >= 0) {
            int modelRow = enrollmentTable.convertRowIndexToModel(selected);
            active = modelRow < enrollments.size()
                    && "ACTIVE".equals(enrollments.get(modelRow).enrollmentStatus());
        }
        drop.setEnabled(adjustmentOpen && active);
        change.setEnabled(adjustmentOpen && active && offeringTable.getSelectedRow() >= 0);
    }

    static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) { return false; }
        };
    }
}
