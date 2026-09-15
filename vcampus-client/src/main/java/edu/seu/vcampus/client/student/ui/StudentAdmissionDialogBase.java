package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.Year;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Widgets, shared helpers and dialog shell for the admission dialog segments. */
abstract class StudentAdmissionDialogBase extends JDialog {
    static final Border SUBMIT_BORDER = BorderFactory.createCompoundBorder(UiBorders.LINE,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2, UiSpacing.SPACE_4,
                    UiSpacing.SPACE_2, UiSpacing.SPACE_4));
    static final Border SUBMIT_FOCUS_BORDER = BorderFactory.createCompoundBorder(UiBorders.FOCUS,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1,
                    UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1));

    final StudentClientService students;
    final AtomicLong requestGeneration = new AtomicLong();
    final JTextField studentName = textField("student.admission.name", "学生姓名");
    final JComboBox<String> gender = comboBox("student.admission.gender", "性别",
            new String[]{"请选择", "男", "女"});
    final JComboBox<String> studentType = comboBox("student.admission.type", "学生类型",
            new String[]{"请选择", "本科生", "硕士生", "博士生"});
    final JComboBox<DepartmentView> department = new JComboBox<>();
    final JComboBox<MajorView> major = new JComboBox<>();
    final JComboBox<ClassView> classBox = new JComboBox<>();
    final JSpinner year = new JSpinner(new SpinnerNumberModel(
            Year.now().getValue(), 2000, 2099, 1));
    final JTextField email = textField("student.admission.email", "邮箱");
    final JTextField phone = textField("student.admission.phone", "电话");
    final JLabel error = new JLabel(" ");
    final JButton cancel = new JButton("取消");
    final JButton submit = new JButton("提交");
    boolean disposed;
    boolean initialFocusEstablished;

    StudentAdmissionDialogBase(Window owner, StudentClientService students) {
        super(owner, "录取新生", ModalityType.APPLICATION_MODAL);
        this.students = Objects.requireNonNull(students, "students");
        department.setName("student.admission.department");
        department.getAccessibleContext().setAccessibleName("院系");
        major.setName("student.admission.major");
        major.getAccessibleContext().setAccessibleName("专业");
        classBox.setName("student.admission.class");
        classBox.getAccessibleContext().setAccessibleName("班级");
        year.setName("student.admission.year");
        year.getAccessibleContext().setAccessibleName("入学年份");
        error.setName("student.admission.error");
        error.getAccessibleContext().setAccessibleName("录取提示");
        cancel.setName("student.admission.cancel");
        cancel.getAccessibleContext().setAccessibleName("取消");
        submit.setName("student.admission.submit");
        submit.getAccessibleContext().setAccessibleName("提交");
        studentName.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { initialFocusEstablished = true; }
        });
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        initializeDialog(owner);
    }

    /** Completes construction once the form, loaders and listeners are available. */
    abstract void initializeDialog(Window owner);

    void establishInitialFocus() {
        SwingUtilities.invokeLater(() -> {
            if (isShowing() && !initialFocusEstablished) studentName.requestFocusInWindow();
        });
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

    static JTextField textField(String name, String accessibleName) {
        JTextField result = new JTextField(24);
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.setBorder(UiBorders.LINE);
        result.getAccessibleContext().setAccessibleName(accessibleName);
        return result;
    }

    static JComboBox<String> comboBox(String name, String accessibleName, String[] items) {
        JComboBox<String> result = new JComboBox<>(items);
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.getAccessibleContext().setAccessibleName(accessibleName);
        return result;
    }

    @SuppressWarnings("unchecked")
    static <T> void replaceItems(JComboBox<T> combo, List<T> items) {
        combo.removeAllItems();
        combo.addItem(null);
        for (T item : items) combo.addItem(item);
    }
}
