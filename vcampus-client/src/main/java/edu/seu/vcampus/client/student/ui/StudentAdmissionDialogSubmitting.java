package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.StudentAdmissionResult;
import edu.seu.vcampus.common.student.StudentType;

import java.awt.Window;
import java.util.concurrent.CompletableFuture;

/** Validation and submission flow for the admission dialog segments. */
abstract class StudentAdmissionDialogSubmitting extends StudentAdmissionDialogSuccess {

    StudentAdmissionDialogSubmitting(Window owner, StudentClientService students) {
        super(owner, students);
    }

    void submit() {
        if (disposed) return;
        String name = studentName.getText();
        if (name == null || name.isBlank()) {
            error.setText("请输入学生姓名");
            studentName.requestFocusInWindow();
            return;
        }
        int genderIndex = gender.getSelectedIndex();
        if (genderIndex <= 0) {
            error.setText("请选择性别");
            gender.requestFocusInWindow();
            return;
        }
        int typeIndex = studentType.getSelectedIndex();
        if (typeIndex <= 0) {
            error.setText("请选择学生类型");
            studentType.requestFocusInWindow();
            return;
        }
        Object selectedMajor = major.getSelectedItem();
        if (!(selectedMajor instanceof MajorView maj)) {
            error.setText("请选择专业");
            major.requestFocusInWindow();
            return;
        }
        Object selectedClass = classBox.getSelectedItem();
        if (!(selectedClass instanceof ClassView cls)) {
            error.setText("请选择班级");
            classBox.requestFocusInWindow();
            return;
        }
        String genderValue = genderIndex == 1 ? "MALE" : "FEMALE";
        StudentType typeValue = switch (typeIndex) {
            case 1 -> StudentType.UNDERGRADUATE;
            case 2 -> StudentType.MASTER;
            case 3 -> StudentType.DOCTORATE;
            default -> StudentType.UNDERGRADUATE;
        };
        String emailValue = blankToNull(email.getText());
        String phoneValue = blankToNull(phone.getText());
        long generation = requestGeneration.incrementAndGet();
        setSaving(true);
        CompletableFuture<ResponseBody<StudentAdmissionResult>> response;
        try {
            response = students.admit(new CreateStudentAdmissionCommand(
                    name.trim(), genderValue, emailValue, phoneValue,
                    maj.majorId(), cls.classId(),
                    (Integer) year.getValue(), typeValue));
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> finishSubmit(generation, body, failure)));
    }

    private void finishSubmit(long generation, ResponseBody<StudentAdmissionResult> body, Throwable failure) {
        if (disposed || generation != requestGeneration.get()) return;
        if (failure != null) {
            setSaving(false);
            error.setText("提交失败，请稍后重试");
            return;
        }
        if (body != null && body.success() && body.data() != null) {
            showSuccess(body.data());
            return;
        }
        setSaving(false);
        error.setText(safeMessage(body, "提交失败，请稍后重试"));
    }

    private void setSaving(boolean saving) {
        studentName.setEnabled(!saving);
        gender.setEnabled(!saving);
        studentType.setEnabled(!saving);
        department.setEnabled(!saving);
        major.setEnabled(!saving);
        classBox.setEnabled(!saving);
        year.setEnabled(!saving);
        email.setEnabled(!saving);
        phone.setEnabled(!saving);
        submit.setEnabled(!saving);
        cancel.setEnabled(!saving);
        submit.setText(saving ? "正在提交" : "提交");
        if (saving) error.setText(" ");
    }
}
