package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Widgets, shared helpers and dialog shell for the enrollment change segments. */
abstract class EnrollmentChangeDialogBase extends JDialog {
    static final Border SUBMIT_BORDER = BorderFactory.createCompoundBorder(UiBorders.LINE,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2, UiSpacing.SPACE_4,
                    UiSpacing.SPACE_2, UiSpacing.SPACE_4));
    static final Border SUBMIT_FOCUS_BORDER = BorderFactory.createCompoundBorder(UiBorders.FOCUS,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1,
                    UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1));

    final StudentClientService students;
    final Consumer<StudentView> saved;
    final AtomicLong requestGeneration = new AtomicLong();
    final JComboBox<DepartmentView> departmentCombo = combo("student.enrollment.department");
    final JComboBox<MajorView> majorCombo = combo("student.enrollment.major");
    final JComboBox<ClassView> classCombo = combo("student.enrollment.class");
    final JTextField effectiveDateField = field("student.enrollment.effective-date", "生效日期");
    final JTextField reasonField = field("student.enrollment.reason", "变更原因");
    final JLabel error = label("student.enrollment.error", "变更班级提示");
    final JButton refresh = button("刷新数据", "student.enrollment.refresh");
    final JButton cancel = button("取消", "student.enrollment.cancel");
    final JButton submit = button("提交变更", "student.enrollment.submit");
    final JLabel studentNameLabel = label("student.enrollment.student-name", "学生姓名");
    final JLabel currentClassLabel = label("student.enrollment.current-class", "当前班级");
    StudentView base;
    boolean conflict;
    boolean disposed;
    boolean published;
    boolean suppressingEvents;

    EnrollmentChangeDialogBase(Window owner, StudentClientService students,
                                  StudentView initial, Consumer<StudentView> saved) {
        super(owner, "变更班级", ModalityType.APPLICATION_MODAL);
        this.students = Objects.requireNonNull(students, "students");
        this.base = Objects.requireNonNull(initial, "initial");
        this.saved = Objects.requireNonNull(saved, "saved");
        studentNameLabel.setText(initial.studentName());
        currentClassLabel.setText(initial.classId());
        effectiveDateField.setText(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        initializeDialog(owner);
    }

    /** Completes construction once the form, loaders and actions are available. */
    abstract void initializeDialog(Window owner);

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

    @SuppressWarnings("unchecked")
    static <E> JComboBox<E> combo(String name) {
        JComboBox<E> result = new JComboBox<>();
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.setBorder(UiBorders.LINE);
        result.getAccessibleContext().setAccessibleName(name);
        return result;
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
