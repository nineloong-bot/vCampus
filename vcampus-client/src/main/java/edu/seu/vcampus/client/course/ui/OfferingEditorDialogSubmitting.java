package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.course.service.CourseClientException;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.OfferingView;
import edu.seu.vcampus.common.course.UpdateOfferingCommand;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Window;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Create/update submission and failure messaging for the offering editor segments. */
abstract class OfferingEditorDialogSubmitting extends OfferingEditorDialogForm {

    OfferingEditorDialogSubmitting(Window owner, CourseUiGateway gateway, OfferingSummary existing,
            Runnable onSaved) {
        super(owner, gateway, existing, onSaved);
    }

    void submit() {
        error.setText(" ");
        if (!referenceReady) {
            error.setText("请等待参考数据加载完成后再保存");
            return;
        }
        CompletableFuture<OfferingView> request;
        try {
            String cleanTerm = requiredChoice(term, "请选择学期");
            String cleanCourse = requiredChoice(course, "请选择课程");
            String cleanTeacher = requiredChoice(teacher, "请选择教师");
            String cleanClass = required(className, "请输入教学班名称");
            int cleanCapacity = ((Number) capacity.getValue()).intValue();
            int cleanRetakeCapacity = ((Number) retakeCapacity.getValue()).intValue();
            if (existing != null && cleanCapacity < existing.enrolledCount()) {
                throw new IllegalArgumentException("容量不能小于当前已选人数 " + existing.enrolledCount());
            }
            if (existing != null && cleanRetakeCapacity < existing.retakeEnrolledCount()) {
                throw new IllegalArgumentException("重修容量不能小于当前重修人数 "
                        + existing.retakeEnrolledCount());
            }
            StatusChoice cleanStatus = (StatusChoice) status.getSelectedItem();
            List<CreateOfferingCommand.ScheduleInput> cleanSchedules = schedules.scheduleInputs();
            if (existing == null) {
                request = gateway.createOffering(new CreateOfferingCommand(cleanTerm, cleanCourse, cleanTeacher,
                        cleanClass, cleanCapacity, cleanRetakeCapacity, cleanStatus.code(), cleanSchedules));
            } else {
                request = gateway.updateOffering(new UpdateOfferingCommand(existing.offeringId(), cleanTerm,
                        cleanCourse, cleanTeacher, cleanClass, cleanCapacity, cleanRetakeCapacity, cleanStatus.code(),
                        existing.rowVersion(), cleanSchedules));
            }
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage() == null ? "请检查教学班字段和上课安排" : invalid.getMessage());
            return;
        }
        String idle = save.getText();
        save.setEnabled(false);
        save.setText(existing == null ? "正在创建…" : "正在保存…");
        long asyncRequest = asyncGuard.begin();
        request.whenComplete((saved, failure) -> SwingUtilities.invokeLater(() -> {
            if (!asyncGuard.accepts(asyncRequest)) return;
            save.setEnabled(referenceReady);
            save.setText(idle);
            if (failure != null) { error.setText(saveFailure(failure)); return; }
            onSaved.run();
            dispose();
        }));
    }

    static String required(JTextField field, String message) {
        String value = field.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(message);
        return value;
    }

    private static String saveFailure(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof CourseClientException clientFailure) {
            if ("COMMON_CONCURRENT_MODIFICATION".equals(clientFailure.code())) {
                return "教学班已被其他管理员修改，请刷新并核对最新记录后重试";
            }
            String safe = clientFailure.getMessage() == null || clientFailure.getMessage().isBlank()
                    ? "服务器未提供可显示的错误信息" : clientFailure.getMessage();
            if (clientFailure.traceId() != null && !clientFailure.traceId().isBlank()) {
                return "保存失败：" + safe + "（跟踪编号：" + clientFailure.traceId() + "）";
            }
            return "保存失败：" + safe;
        }
        return "保存失败，请检查连接后重试";
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }
}
