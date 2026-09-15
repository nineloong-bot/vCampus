package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.StudentView;
import edu.seu.vcampus.common.student.UpdateStudentContactCommand;

import java.awt.Window;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Validation and submission flow for the contact dialog segments. */
abstract class UpdateContactDialogSaving extends UpdateContactDialogForm {

    UpdateContactDialogSaving(Window owner, StudentClientService students,
            StudentView initial, Consumer<StudentView> saved) {
        super(owner, students, initial, saved);
    }

    void save() {
        if (disposed || conflict) return;
        String normalizedEmail = blankToNull(email.getText());
        String normalizedPhone = blankToNull(phone.getText());
        if (normalizedEmail != null && !EMAIL.matcher(normalizedEmail).matches()) {
            error.setText("请输入格式正确的邮箱地址");
            email.requestFocusInWindow();
            return;
        }
        long generation = requestGeneration.incrementAndGet();
        setSaving(true);
        CompletableFuture<ResponseBody<StudentView>> response;
        try {
            response = students.updateContact(new UpdateStudentContactCommand(
                    base.studentId(), normalizedEmail, normalizedPhone, base.rowVersion()));
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
        email.setEnabled(!saving);
        phone.setEnabled(!saving);
        submit.setEnabled(!saving);
        cancel.setEnabled(!saving);
        refresh.setEnabled(!saving);
        submit.setText(saving ? "正在保存" : "保存");
        if (saving) error.setText(" ");
    }
}
