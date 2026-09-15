package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.StudentView;
import edu.seu.vcampus.common.student.UpdateStudentEnrollmentCommand;

import java.awt.Window;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Validation and submission flow for the enrollment change segments. */
abstract class EnrollmentChangeDialogSaving extends EnrollmentChangeDialogLoading {

    EnrollmentChangeDialogSaving(Window owner, StudentClientService students,
            StudentView initial, Consumer<StudentView> saved) {
        super(owner, students, initial, saved);
    }

    void save() {
        if (disposed || conflict) return;
        ClassView selectedClass = (ClassView) classCombo.getSelectedItem();
        if (selectedClass == null) {
            error.setText("请选择目标班级");
            classCombo.requestFocusInWindow();
            return;
        }
        String reason = blankToNull(reasonField.getText());
        if (reason == null) {
            error.setText("请填写变更原因");
            reasonField.requestFocusInWindow();
            return;
        }
        LocalDate effectiveDate;
        try {
            effectiveDate = LocalDate.parse(effectiveDateField.getText().trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            error.setText("请输入有效的日期格式（yyyy-MM-dd）");
            effectiveDateField.requestFocusInWindow();
            return;
        }
        long generation = requestGeneration.incrementAndGet();
        setSaving(true);
        CompletableFuture<ResponseBody<StudentView>> response;
        try {
            response = students.updateEnrollment(new UpdateStudentEnrollmentCommand(
                    base.studentId(), selectedClass.classId(), effectiveDate, reason, base.rowVersion()));
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> finishSave(generation, body, failure)));
    }

    private void finishSave(long generation, ResponseBody<StudentView> body, Throwable failure) {
        if (disposed || generation != requestGeneration.get()) return;
        if (failure != null) {
            setSaving(false);
            error.setText("保存失败，请稍后重试");
            return;
        }
        if (body != null && body.success() && body.data() != null) {
            if (published) return;
            published = true;
            dispose();
            saved.accept(body.data());
            return;
        }
        setSaving(false);
        if (body != null && "COMMON_CONCURRENT_MODIFICATION".equals(body.code())) {
            conflict = true;
            submit.setEnabled(false);
            refresh.setVisible(true);
            error.setText("数据已被修改，请刷新数据后确认再保存");
            return;
        }
        error.setText(safeMessage(body, "保存失败，请稍后重试"));
    }

    private void setSaving(boolean saving) {
        departmentCombo.setEnabled(!saving);
        majorCombo.setEnabled(!saving);
        classCombo.setEnabled(!saving);
        effectiveDateField.setEnabled(!saving);
        reasonField.setEnabled(!saving);
        submit.setEnabled(!saving);
        cancel.setEnabled(!saving);
        refresh.setEnabled(!saving);
        submit.setText(saving ? "正在保存" : "提交变更");
        if (saving) error.setText(" ");
    }
}
