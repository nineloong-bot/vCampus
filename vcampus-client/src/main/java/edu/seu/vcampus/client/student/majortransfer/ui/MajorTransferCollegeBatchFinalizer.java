package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.majortransfer.FinalizeMajorTransferBatchCommand;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchReadinessView;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchView;

import javax.swing.*;
import java.awt.Component;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Handles batch readiness loading and finalization for the college transfer panel. */
final class MajorTransferCollegeBatchFinalizer {
    private final Component parent;
    private final StudentClientService students;
    private final JLabel readiness;
    private final JButton finalizeBatch;
    private final JLabel status;
    private final Supplier<MajorTransferBatchView> selectedBatch;
    private final LongSupplier currentBatchRequest;
    private final Runnable onFinalized;

    MajorTransferCollegeBatchFinalizer(Component parent, StudentClientService students,
            JLabel readiness, JButton finalizeBatch, JLabel status,
            Supplier<MajorTransferBatchView> selectedBatch,
            LongSupplier currentBatchRequest, Runnable onFinalized) {
        this.parent = Objects.requireNonNull(parent);
        this.students = Objects.requireNonNull(students);
        this.readiness = Objects.requireNonNull(readiness);
        this.finalizeBatch = Objects.requireNonNull(finalizeBatch);
        this.status = Objects.requireNonNull(status);
        this.selectedBatch = Objects.requireNonNull(selectedBatch);
        this.currentBatchRequest = Objects.requireNonNull(currentBatchRequest);
        this.onFinalized = Objects.requireNonNull(onFinalized);
    }

    void loadReadiness(String batchId, long request) {
        var future = students.getTransferBatchReadiness(batchId);
        if (future == null) return;
        future.whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (request != currentBatchRequest.getAsLong()) return;
                    if (response == null || !response.success()) {
                        readiness.setText("终审状态：" + message(response, "加载失败"));
                        finalizeBatch.setEnabled(false);
                        return;
                    }
                    MajorTransferBatchReadinessView value = response.data();
                    readiness.setText("拟录取 " + value.assessed() + " / 驳回 " + value.rejected()
                            + " / 取消 " + value.cancelled() + " / 未处理 " + value.unresolved()
                            + (value.ready() ? "" : " — " + value.reason()));
                    finalizeBatch.putClientProperty("batchVersion", value.batchVersion());
                    finalizeBatch.setEnabled(value.ready());
                }));
    }

    void finalizeSelectedBatch() {
        MajorTransferBatchView batch = selectedBatch.get();
        Object version = finalizeBatch.getClientProperty("batchVersion");
        if (batch == null || !(version instanceof Long expectedVersion)) return;
        int answer = JOptionPane.showConfirmDialog(parent,
                "将一次性生效本批次全部拟录取学生，并自动分班、换学号和清理选课。是否继续？",
                "确认批次终审", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        finalizeBatch.setEnabled(false);
        var future = students.finalizeTransferBatch(new FinalizeMajorTransferBatchCommand(
                batch.batchId(), expectedVersion));
        if (future == null) return;
        future.whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) {
                        status.setText("已生效 " + response.data().effectiveStudents()
                                + " 名学生，自动退选 "
                                + response.data().droppedEnrollments() + " 条课程");
                        onFinalized.run();
                    } else {
                        status.setText(message(response, "批次终审失败"));
                        loadReadiness(batch.batchId(), currentBatchRequest.getAsLong());
                    }
                }));
    }

    private static String message(ResponseBody<?> response, String fallback) {
        return response == null || response.message() == null ? fallback : response.message();
    }
}
