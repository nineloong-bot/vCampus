package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.StudentAcademicProfile;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Shared state, form fields and small helpers for the admin edit dialog segments. */
abstract class AdminStudentInfoEditDialogBase extends JDialog {
    static final Border SUBMIT_BORDER = BorderFactory.createCompoundBorder(UiBorders.LINE,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2, UiSpacing.SPACE_4,
                    UiSpacing.SPACE_2, UiSpacing.SPACE_4));
    static final Border SUBMIT_FOCUS_BORDER = BorderFactory.createCompoundBorder(UiBorders.FOCUS,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1,
                    UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1));

    protected final StudentClientService students;
    protected final Consumer<StudentView> saved;
    protected final AtomicLong requestGeneration = new AtomicLong();
    protected final AtomicLong hierarchyGeneration = new AtomicLong();
    protected final JTextField studentNumberField = field("student.info.studentNumber", "学号");
    protected final JComboBox<String> studentTypeCombo = new JComboBox<>(
            new String[]{"本科生", "硕士生", "博士生"});
    protected final JComboBox<Object> departmentCombo = new JComboBox<>();
    protected final JComboBox<Object> majorCombo = new JComboBox<>();
    protected final JComboBox<Object> classCombo = new JComboBox<>();
    protected final JComboBox<String> statusCombo = new JComboBox<>(
            new String[]{"正常", "休学", "已毕业", "已退学"});
    protected final JComboBox<String> enrolledCombo = new JComboBox<>(new String[]{"是", "否"});
    protected final JComboBox<String> onCampusCombo = new JComboBox<>(new String[]{"是", "否"});
    protected final JTextField campusField = field("student.info.campus", "校区");
    protected final JTextField educationLevelField = field("student.info.educationLevel", "培养层次");
    protected final JTextField trainingModeField = field("student.info.trainingMode", "培养方式");
    protected final JTextField programLengthField = field("student.info.programLength", "学制（年）");
    protected final JComboBox<String> attendanceModeCombo = new JComboBox<>(
            new String[]{"住校", "走读", "借宿", "其他"});
    protected final JTextField degreeNameField = field("student.info.degreeName", "就读学位");
    protected final JTextField educationNameField = field("student.info.educationName", "就读学历");
    protected final JTextField expectedGraduationField = field("student.info.expectedGraduation", "预计毕业日期");
    protected final JTextField graduationField = field("student.info.graduation", "毕业日期");
    protected final JTextField studentSourceField = field("student.info.studentSource", "学生来源");
    protected final JTextField graduateStudyModeField = field("student.info.graduateStudyMode", "学习形式（研）");
    protected final JTextField counselorNameField = field("student.info.counselorName", "辅导员姓名");
    protected final JTextField counselorContactField = field("student.info.counselorContact", "辅导员联系方式");
    protected final JTextField reasonField = field("student.info.reason", "变更原因");
    protected final JLabel error = label("student.info.error", "提示");
    protected final JButton refresh = button("刷新数据", "student.info.refresh");
    protected final JButton cancel = button("取消", "student.info.cancel");
    protected final JButton submit = button("保存", "student.info.submit");
    protected StudentView base;
    protected StudentAcademicProfile academic;
    protected boolean conflict;
    protected boolean disposed;
    protected boolean published;
    protected boolean suppressComboEvents;

    /** Creates the application-modal dialog and stores the shared dependencies. */
    protected AdminStudentInfoEditDialogBase(Window owner, StudentClientService students,
            StudentView initial, StudentAcademicProfile academic, Consumer<StudentView> saved) {
        super(owner, "编辑学籍信息", ModalityType.APPLICATION_MODAL);
        this.students = Objects.requireNonNull(students, "students");
        this.base = Objects.requireNonNull(initial, "initial");
        this.academic = Objects.requireNonNull(academic, "academic");
        this.saved = Objects.requireNonNull(saved, "saved");
    }

    void styleCombo(JComboBox<String> combo, String name, String accessibleName) {
        combo.setName(name);
        combo.setFont(UiTypography.BODY);
        combo.getAccessibleContext().setAccessibleName(accessibleName);
    }

    String selectedDepartmentId() {
        Object selected = departmentCombo.getSelectedItem();
        return selected instanceof DepartmentView value ? value.departmentId() : null;
    }

    String selectedMajorId() {
        Object selected = majorCombo.getSelectedItem();
        return selected instanceof MajorView value ? value.majorId() : null;
    }

    String selectedClassId() {
        Object selected = classCombo.getSelectedItem();
        return selected instanceof ClassView value ? value.classId() : null;
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String safeMessage(ResponseBody<?> body, String fallback) {
        return body != null && body.message() != null && !body.message().isBlank() ? body.message() : fallback;
    }

    static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    static JTextField field(String name, String accessibleName) {
        JTextField result = new JTextField(24);
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.setBorder(UiBorders.LINE);
        result.getAccessibleContext().setAccessibleName(accessibleName);
        return result;
    }

    static JButton button(String title, String name) {
        JButton result = new JButton(title);
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.getAccessibleContext().setAccessibleName(title);
        return result;
    }

    static JLabel label(String name, String accessibleName) {
        JLabel result = new JLabel(" ");
        result.setName(name);
        result.getAccessibleContext().setAccessibleName(accessibleName);
        return result;
    }
}
