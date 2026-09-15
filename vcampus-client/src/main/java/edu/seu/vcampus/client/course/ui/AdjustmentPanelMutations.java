package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Add, drop and change mutations for the adjustment panel segments. */
abstract class AdjustmentPanelMutations extends AdjustmentPanelFormatting {

    AdjustmentPanelMutations(CourseUiGateway gateway) {
        super(gateway);
    }

    AdjustmentPanelMutations(CourseUiGateway gateway, ChangeConfirmation confirmation) {
        super(gateway, confirmation);
    }

    void lateAdd(JButton button) {
        if (mutationPending) return;
        int target = offeringTable.getSelectedRow();
        if (target < 0) { showState(ViewState.ERROR, "请先选择要补选的教学班"); return; }
        submit(button, "正在补选…",
                () -> gateway.lateAdd(new LateAddCommand(offerings.get(target).offeringId())),
                "补选成功，可在我的选课查看");
    }

    void drop(JButton button) {
        if (mutationPending) return;
        int source = enrollmentTable.getSelectedRow();
        if (source < 0) { showState(ViewState.ERROR, "请先选择要退选的记录"); return; }
        EnrollmentView selected = enrollments.get(enrollmentTable.convertRowIndexToModel(source));
        if (!"ACTIVE".equals(selected.enrollmentStatus())) {
            showState(ViewState.ERROR, "该选课记录已退选，请刷新后重试");
            return;
        }
        submit(button, "正在退选…",
                () -> gateway.drop(new DropCommand(selected.enrollmentId(), selected.rowVersion())),
                "退选成功，课表已更新");
    }

    void change(JButton button) {
        if (mutationPending) return;
        int source = enrollmentTable.getSelectedRow();
        int target = offeringTable.getSelectedRow();
        if (source < 0 || target < 0) { showState(ViewState.ERROR, "请同时选择原选课记录和目标教学班"); return; }
        EnrollmentView selected = enrollments.get(enrollmentTable.convertRowIndexToModel(source));
        if (!"ACTIVE".equals(selected.enrollmentStatus())) {
            showState(ViewState.ERROR, "该选课记录已退选，请刷新后重试");
            return;
        }
        OfferingSummary targetOffering = offerings.get(offeringTable.convertRowIndexToModel(target));
        OfferingSummary sourceOffering = findOffering(selected.offeringId());
        if (sourceOffering == null) { showState(ViewState.ERROR, "原教学班信息尚未同步，请刷新后重试"); return; }
        String conflict = conflictResult(sourceOffering, targetOffering);
        confirmation.show(SwingUtilities.getWindowAncestor(this), sourceOffering, targetOffering, conflict,
                () -> trackedChange(new ChangeOfferingCommand(
                        selected.enrollmentId(), targetOffering.offeringId(), selected.rowVersion())),
                () -> refresh("改选成功，已刷新选课与教学班状态"));
    }

    private String conflictResult(OfferingSummary source, OfferingSummary target) {
        for (ScheduleItem targetTime : target.schedules()) {
            for (ScheduleItem existing : currentSchedule) {
                if (existing.offeringId().equals(source.offeringId())) continue;
                if (overlaps(targetTime, existing)) {
                    return "发现时间冲突：" + existing.courseName() + " · " + existing.className();
                }
            }
        }
        return "未发现时间冲突（服务端提交时将再次校验）";
    }

    private void submit(JButton button, String busyText, Supplier<CompletableFuture<?>> requestFactory, String success) {
        long mutation = beginMutation();
        if (mutation < 0) return;
        long asyncRequest = beginAsyncRequest();
        String idleText = button.getText();
        button.setText(busyText);
        showState(ViewState.SUBMITTING, busyText + " 请勿重复操作");
        CompletableFuture<?> request;
        try {
            request = requestFactory.get();
        } catch (Throwable error) {
            request = CompletableFuture.failedFuture(error);
        }
        request.whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
            if (mutation != mutationSequence) return;
            mutationPending = false;
            if (!acceptsAsyncResult(asyncRequest)) return;
            button.setText(idleText);
            updateEnrollmentActions();
            if (error == null) {
                refresh(success);
                return;
            }
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

    private CompletableFuture<?> trackedChange(ChangeOfferingCommand command) {
        long mutation = beginMutation();
        if (mutation < 0) {
            return CompletableFuture.failedFuture(new IllegalStateException("已有调整操作正在进行"));
        }
        CompletableFuture<?> request;
        try {
            request = gateway.change(command);
        } catch (Throwable error) {
            request = CompletableFuture.failedFuture(error);
        }
        request.whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
            if (mutation != mutationSequence) return;
            mutationPending = false;
            updateEnrollmentActions();
        }));
        return request;
    }

    private long beginMutation() {
        if (mutationPending) return -1;
        mutationPending = true;
        long mutation = ++mutationSequence;
        setActionButtonsEnabled(false);
        return mutation;
    }

    /** Reloads the data and reports the given success message. */
    abstract void refresh(String successMessage);
}
