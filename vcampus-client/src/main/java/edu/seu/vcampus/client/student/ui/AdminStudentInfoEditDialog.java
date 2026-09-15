package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.StudentAcademicProfile;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * Modal editor for admin to edit all academic fields of a student record.
 *
 * <p>The form assembly, hierarchy loading and save flows are implemented by
 * the package-private segment chain this class extends, keeping the public
 * surface unchanged.</p>
 */
public final class AdminStudentInfoEditDialog extends AdminStudentInfoEditDialogForm {

    /**
     * Creates the application-modal editor bound to the given student.
     *
     * @param owner parent window for modality and centering
     * @param students service used for academic data exchanges
     * @param initial current student view used to seed the form
     * @param academic current academic profile used to seed the form
     * @param saved callback receiving the updated student view on success
     */
    public AdminStudentInfoEditDialog(Window owner, StudentClientService students,
                                      StudentView initial, StudentAcademicProfile academic,
                                      Consumer<StudentView> saved) {
        super(owner, students, initial, academic, saved);
        studentNumberField.setText(initial.studentNumber());
        studentTypeCombo.setSelectedItem(studentTypeLabel(initial.studentType()));
        statusCombo.setSelectedItem(statusLabel(initial.status()));
        enrolledCombo.setSelectedItem(academic.enrolled() ? "是" : "否");
        onCampusCombo.setSelectedItem(academic.onCampus() ? "是" : "否");
        campusField.setText(academic.campus());
        educationLevelField.setText(academic.educationLevel());
        trainingModeField.setText(academic.trainingMode());
        programLengthField.setText(academic.programLengthYears() == null ? "" : String.valueOf(academic.programLengthYears()));
        attendanceModeCombo.setSelectedItem(academic.attendanceMode() == null ? "住校" : academic.attendanceMode().displayName());
        degreeNameField.setText(academic.degreeName());
        educationNameField.setText(academic.educationName());
        expectedGraduationField.setText(academic.expectedGraduationDate() == null ? "" : academic.expectedGraduationDate().toString());
        graduationField.setText(academic.graduationDate() == null ? "" : academic.graduationDate().toString());
        studentSourceField.setText(academic.studentSource());
        graduateStudyModeField.setText(academic.graduateStudyMode());
        counselorNameField.setText(academic.counselorName());
        counselorContactField.setText(academic.counselorContact());
        styleCombo(studentTypeCombo, "student.info.studentType", "学生类别");
        styleCombo(statusCombo, "student.info.status", "学籍状态");
        styleCombo(enrolledCombo, "student.info.enrolled", "是否在籍");
        styleCombo(onCampusCombo, "student.info.onCampus", "是否在校");
        styleCombo(attendanceModeCombo, "student.info.attendanceMode", "就读方式");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildForm());
        initComboListeners();
        loadDepartments();
        refresh.setVisible(false);
        refresh.addActionListener(event -> refreshBase());
        cancel.addActionListener(event -> dispose());
        submit.addActionListener(event -> save());
        getRootPane().setDefaultButton(submit);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setFocusCycleRoot(true);
        setSize(new Dimension(620, 680));
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    @Override public void dispose() {
        disposed = true;
        requestGeneration.incrementAndGet();
        hierarchyGeneration.incrementAndGet();
        super.dispose();
    }
}
