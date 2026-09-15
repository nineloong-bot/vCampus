package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.AttendanceMode;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.StudentAcademicProfile;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.StudentView;
import edu.seu.vcampus.common.student.UpdateStudentAcademicCommand;

import java.awt.*;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Validation, submission and field-toggling for the admin edit dialog. */
abstract class AdminStudentInfoEditDialogSaving extends AdminStudentInfoEditDialogRefreshing {

    /** Creates the saving segment of the admin edit dialog. */
    protected AdminStudentInfoEditDialogSaving(Window owner, StudentClientService students,
            StudentView initial, StudentAcademicProfile academic, Consumer<StudentView> saved) {
        super(owner, students, initial, academic, saved);
    }

    void save() {
        if (disposed || conflict) return;
        String studentNumber = blankToNull(studentNumberField.getText());
        String reason = blankToNull(reasonField.getText());
        if (studentNumber == null || studentNumber.length() != 8) {
            error.setText("请输入8位学号");
            studentNumberField.requestFocusInWindow();
            return;
        }
        Object classItem = classCombo.getSelectedItem();
        String classId = null;
        if (classItem instanceof ClassView cv) classId = cv.classId();
        if (classId == null) {
            error.setText("请选择班级");
            classCombo.requestFocusInWindow();
            return;
        }
        if (reason == null) {
            error.setText("请填写变更原因");
            reasonField.requestFocusInWindow();
            return;
        }
        StudentType newType = parseStudentType((String) studentTypeCombo.getSelectedItem());
        StudentStatus newStatus = parseStatus((String) statusCombo.getSelectedItem());
        Boolean enrolled = parseYesNo((String) enrolledCombo.getSelectedItem());
        Boolean onCampus = parseYesNo((String) onCampusCombo.getSelectedItem());
        Integer programLength = parseProgramLength(programLengthField.getText());
        if (programLengthField.getText() != null && !programLengthField.getText().isBlank()
                && programLength == null) {
            error.setText("学制必须为数字");
            programLengthField.requestFocusInWindow();
            return;
        }
        AttendanceMode attendanceMode = AttendanceMode.fromDisplayName((String) attendanceModeCombo.getSelectedItem());
        LocalDate expectedGraduation = parseDate(expectedGraduationField.getText());
        if (expectedGraduationField.getText() != null && !expectedGraduationField.getText().isBlank()
                && expectedGraduation == null) {
            error.setText("预计毕业日期格式应为 YYYY-MM-DD");
            expectedGraduationField.requestFocusInWindow();
            return;
        }
        LocalDate graduation = parseDate(graduationField.getText());
        if (graduationField.getText() != null && !graduationField.getText().isBlank()
                && graduation == null) {
            error.setText("毕业日期格式应为 YYYY-MM-DD");
            graduationField.requestFocusInWindow();
            return;
        }
        boolean changed = !studentNumber.equals(base.studentNumber())
                || !classId.equals(base.classId())
                || newStatus != base.status()
                || newType != base.studentType()
                || !Objects.equals(enrolled, academic.enrolled())
                || !Objects.equals(onCampus, academic.onCampus())
                || !Objects.equals(blankToNull(campusField.getText()), blankToNull(academic.campus()))
                || !Objects.equals(blankToNull(educationLevelField.getText()), blankToNull(academic.educationLevel()))
                || !Objects.equals(blankToNull(trainingModeField.getText()), blankToNull(academic.trainingMode()))
                || !Objects.equals(programLength, academic.programLengthYears())
                || attendanceMode != academic.attendanceMode()
                || !Objects.equals(blankToNull(degreeNameField.getText()), blankToNull(academic.degreeName()))
                || !Objects.equals(blankToNull(educationNameField.getText()), blankToNull(academic.educationName()))
                || !Objects.equals(expectedGraduation, academic.expectedGraduationDate())
                || !Objects.equals(graduation, academic.graduationDate())
                || !Objects.equals(blankToNull(studentSourceField.getText()), blankToNull(academic.studentSource()))
                || !Objects.equals(blankToNull(graduateStudyModeField.getText()), blankToNull(academic.graduateStudyMode()))
                || !Objects.equals(blankToNull(counselorNameField.getText()), blankToNull(academic.counselorName()))
                || !Objects.equals(blankToNull(counselorContactField.getText()), blankToNull(academic.counselorContact()));
        if (!changed) {
            error.setText("未做任何修改");
            return;
        }
        long generation = requestGeneration.incrementAndGet();
        setSaving(true);
        CompletableFuture<ResponseBody<StudentView>> response;
        try {
            response = students.updateStudentAcademic(new UpdateStudentAcademicCommand(
                    base.studentId(), studentNumber, classId, newType, newStatus,
                    enrolled, onCampus, blankToNull(campusField.getText()),
                    blankToNull(educationLevelField.getText()), blankToNull(trainingModeField.getText()),
                    programLength, attendanceMode, blankToNull(degreeNameField.getText()),
                    blankToNull(educationNameField.getText()), expectedGraduation, graduation,
                    blankToNull(studentSourceField.getText()), blankToNull(graduateStudyModeField.getText()),
                    blankToNull(counselorNameField.getText()), blankToNull(counselorContactField.getText()),
                    LocalDate.now(), reason, base.rowVersion()));
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> finishSave(generation, body, failure)));
    }

    void finishSave(long generation, ResponseBody<StudentView> body, Throwable failure) {
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

    void setSaving(boolean saving) {
        studentNumberField.setEnabled(!saving);
        studentTypeCombo.setEnabled(!saving);
        departmentCombo.setEnabled(!saving);
        majorCombo.setEnabled(!saving);
        classCombo.setEnabled(!saving);
        statusCombo.setEnabled(!saving);
        enrolledCombo.setEnabled(!saving);
        onCampusCombo.setEnabled(!saving);
        campusField.setEnabled(!saving);
        educationLevelField.setEnabled(!saving);
        trainingModeField.setEnabled(!saving);
        programLengthField.setEnabled(!saving);
        attendanceModeCombo.setEnabled(!saving);
        degreeNameField.setEnabled(!saving);
        educationNameField.setEnabled(!saving);
        expectedGraduationField.setEnabled(!saving);
        graduationField.setEnabled(!saving);
        studentSourceField.setEnabled(!saving);
        graduateStudyModeField.setEnabled(!saving);
        counselorNameField.setEnabled(!saving);
        counselorContactField.setEnabled(!saving);
        reasonField.setEnabled(!saving);
        submit.setEnabled(!saving);
        cancel.setEnabled(!saving);
        refresh.setEnabled(!saving);
        submit.setText(saving ? "正在保存" : "保存");
        if (saving) error.setText(" ");
    }
}
