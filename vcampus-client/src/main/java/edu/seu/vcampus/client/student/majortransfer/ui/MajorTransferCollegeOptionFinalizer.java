package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.majortransfer.*;

import javax.swing.*;
import java.awt.Component;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Handles readiness and lifecycle actions for one selected transfer option. */
final class MajorTransferCollegeOptionFinalizer {
    private final Component parent;
    private final StudentClientService students;
    private final JLabel readiness;
    private final JButton finalizeOption;
    private final JButton effectiveOption;
    private final JButton rollbackOption;
    private final JLabel status;
    private final Supplier<MajorTransferOptionView> selectedOption;
    private final LongSupplier currentRequest;
    private final Runnable onChanged;

    MajorTransferCollegeOptionFinalizer(Component parent, StudentClientService students,
            JLabel readiness, JButton finalizeOption, JButton effectiveOption,
            JButton rollbackOption, JLabel status,
            Supplier<MajorTransferOptionView> selectedOption,
            LongSupplier currentRequest, Runnable onChanged) {
        this.parent = Objects.requireNonNull(parent);
        this.students = Objects.requireNonNull(students);
        this.readiness = Objects.requireNonNull(readiness);
        this.finalizeOption = Objects.requireNonNull(finalizeOption);
        this.effectiveOption = Objects.requireNonNull(effectiveOption);
        this.rollbackOption = Objects.requireNonNull(rollbackOption);
        this.status = Objects.requireNonNull(status);
        this.selectedOption = Objects.requireNonNull(selectedOption);
        this.currentRequest = Objects.requireNonNull(currentRequest);
        this.onChanged = Objects.requireNonNull(onChanged);
    }

    void loadReadiness(String optionId, long request) {
        var future = students.getTransferOptionReadiness(optionId);
        if (future == null) return;
        future.whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
            if (request != currentRequest.getAsLong()) return;
            if (response == null || !response.success()) {
                readiness.setText("终审状态：" + message(response, "加载失败"));
                setActions(false, false, false);
                return;
            }
            MajorTransferOptionReadinessView value = response.data();
            readiness.setText(summarize(value));
            finalizeOption.putClientProperty("optionVersion", value.optionVersion());
            setActions(value.canReview(), value.canEffect(), value.canRollback());
        }));
    }

    void finalizeSelectedOption() {
        var option = selectedOption.get();
        Long version = version();
        if (option == null || version == null) return;
        if (!confirm("仅对“" + option.targetMajorName()
                + "”的已评定申请进行终审，是否继续？", "确认专业终审")) return;
        finalizeOption.setEnabled(false);
        var future = students.finalizeTransferOption(
                new FinalizeMajorTransferOptionCommand(option.optionId(), version));
        future.whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
            if (response != null && response.success()) {
                status.setText(option.targetMajorName() + "终审完成，"
                        + response.data().preparedApplications() + " 份申请待生效");
                onChanged.run();
            } else fail(response, "专业终审失败", option);
        }));
    }

    void effectiveSelectedOption() {
        var option = selectedOption.get();
        Long version = version();
        if (option == null || version == null) return;
        if (!confirm("生效后将修改该专业录取学生的学籍，且不可重复。是否继续？",
                "确认专业生效")) return;
        effectiveOption.setEnabled(false);
        var future = students.effectiveTransferOption(
                new EffectiveMajorTransferOptionCommand(option.optionId(), version));
        future.whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
            if (response != null && response.success()) {
                status.setText(option.targetMajorName() + "已生效 "
                        + response.data().effectiveStudents() + " 名学生");
                onChanged.run();
            } else fail(response, "专业生效失败", option);
        }));
    }

    void rollbackSelectedOption() {
        var option = selectedOption.get();
        Long version = version();
        if (option == null || version == null) return;
        if (!confirm("回退后该专业的终审结果将恢复为已评定，是否继续？",
                "确认回退专业终审")) return;
        rollbackOption.setEnabled(false);
        var future = students.rollbackTransferOption(
                new RollbackMajorTransferOptionCommand(option.optionId(), version));
        future.whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
            if (response != null && response.success()) {
                status.setText("已回退 " + option.targetMajorName() + " 的 "
                        + response.data().restoredApplications() + " 份申请");
                onChanged.run();
            } else fail(response, "回退终审失败", option);
        }));
    }

    private void fail(ResponseBody<?> response, String fallback, MajorTransferOptionView option) {
        status.setText(message(response, fallback));
        loadReadiness(option.optionId(), currentRequest.getAsLong());
    }

    private Long version() {
        Object value = finalizeOption.getClientProperty("optionVersion");
        return value instanceof Long number ? number : null;
    }

    private boolean confirm(String text, String title) {
        return JOptionPane.showConfirmDialog(parent, text, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void setActions(boolean review, boolean effect, boolean rollback) {
        finalizeOption.setEnabled(review);
        effectiveOption.setEnabled(effect);
        rollbackOption.setEnabled(rollback);
    }

    private static String message(ResponseBody<?> response, String fallback) {
        return response == null || response.message() == null ? fallback : response.message();
    }

    private static String summarize(MajorTransferOptionReadinessView value) {
        String counts = "已评定 " + value.assessed() + " / 待生效 " + value.pendingEffective()
                + " / 驳回 " + value.rejected() + " / 取消 " + value.cancelled()
                + " / 未处理 " + value.unresolved();
        String reason = value.reason() == null ? "" : " — " + value.reason();
        return value.targetMajorName() + "：" + counts + reason;
    }
}
