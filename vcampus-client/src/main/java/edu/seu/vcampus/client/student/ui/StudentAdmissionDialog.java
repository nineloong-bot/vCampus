package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class StudentAdmissionDialog extends JDialog {
    private static final Border SUBMIT_BORDER = BorderFactory.createCompoundBorder(UiBorders.LINE,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2, UiSpacing.SPACE_4,
                    UiSpacing.SPACE_2, UiSpacing.SPACE_4));
    private static final Border SUBMIT_FOCUS_BORDER = BorderFactory.createCompoundBorder(UiBorders.FOCUS,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1,
                    UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1));

    private final StudentClientService students;
    private final AtomicLong requestGeneration = new AtomicLong();
    private final JTextField studentName = textField("student.admission.name", "学生姓名");
    private final JComboBox<String> gender = comboBox("student.admission.gender", "性别",
            new String[]{"请选择", "男", "女"});
    private final JComboBox<String> studentType = comboBox("student.admission.type", "学生类型",
            new String[]{"请选择", "本科生", "硕士生", "博士生"});
    private final JComboBox<DepartmentView> department = new JComboBox<>();
    private final JComboBox<MajorView> major = new JComboBox<>();
    private final JComboBox<ClassView> classBox = new JComboBox<>();
    private final JSpinner year = new JSpinner(new SpinnerNumberModel(
            Year.now().getValue(), 2000, 2099, 1));
    private final JTextField email = textField("student.admission.email", "邮箱");
    private final JTextField phone = textField("student.admission.phone", "电话");
    private final JLabel error = new JLabel(" ");
    private final JButton cancel = new JButton("取消");
    private final JButton submit = new JButton("提交");
    private boolean disposed;
    private boolean initialFocusEstablished;

    public StudentAdmissionDialog(Window owner, StudentClientService students) {
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
        setContentPane(buildForm());
        loadDepartments();
        cancel.addActionListener(event -> dispose());
        submit.addActionListener(event -> submit());
        getRootPane().setDefaultButton(submit);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new AdmissionFocusTraversalPolicy());
        department.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                Object selected = department.getSelectedItem();
                if (selected instanceof DepartmentView dept) {
                    loadMajors(dept.departmentId());
                }
            }
        });
        major.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                Object selected = major.getSelectedItem();
                if (selected instanceof MajorView maj) {
                    loadClasses(maj.majorId());
                }
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) { establishInitialFocus(); }
            @Override public void windowActivated(WindowEvent event) { establishInitialFocus(); }
        });
        setSize(new Dimension(600, 520));
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JPanel buildForm() {
        JPanel content = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        content.setBackground(UiColors.BACKGROUND_PAGE);
        content.setBorder(UiBorders.pageInset());
        JLabel title = new JLabel("录取新生");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        content.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        constraints.anchor = GridBagConstraints.WEST;
        addTextFieldRow(form, constraints, "学生姓名", studentName, 0);
        addComboRow(form, constraints, "性别", gender, 1);
        addComboRow(form, constraints, "学生类型", studentType, 2);
        addComboRow(form, constraints, "院系", department, 3);
        addComboRow(form, constraints, "专业", major, 4);
        addComboRow(form, constraints, "班级", classBox, 5);
        addSpinnerRow(form, constraints, "入学年份", year, 6);
        addTextFieldRow(form, constraints, "邮箱", email, 7);
        addTextFieldRow(form, constraints, "电话", phone, 8);
        content.add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, UiSpacing.SPACE_2));
        bottom.setOpaque(false);
        error.setForeground(UiColors.ERROR_FG);
        error.setFont(UiTypography.CAPTION);
        bottom.add(error, BorderLayout.NORTH);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        left.setOpaque(false);
        left.add(cancel);
        bottom.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        submit.setUI(new BasicButtonUI());
        submit.setOpaque(true);
        submit.setContentAreaFilled(true);
        submit.setBorder(SUBMIT_BORDER);
        submit.setFocusPainted(true);
        submit.setBackground(UiColors.ACCENT);
        submit.setForeground(UiColors.TEXT_ON_PRIMARY);
        submit.setFont(UiTypography.BODY);
        submit.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { submit.setBorder(SUBMIT_FOCUS_BORDER); }
            @Override public void focusLost(FocusEvent event) { submit.setBorder(SUBMIT_BORDER); }
        });
        right.add(submit);
        bottom.add(right, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);
        return content;
    }

    private void addTextFieldRow(JPanel form, GridBagConstraints constraints,
                                 String title, JTextField field, int row) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(title);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        form.add(field, constraints);
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    private void addComboRow(JPanel form, GridBagConstraints constraints,
                             String title, JComboBox<?> combo, int row) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(title);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        form.add(combo, constraints);
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    private void addSpinnerRow(JPanel form, GridBagConstraints constraints,
                               String title, JSpinner spinner, int row) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(title);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        spinner.setFont(UiTypography.BODY);
        form.add(spinner, constraints);
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    private void loadDepartments() {
        long generation = requestGeneration.get();
        CompletableFuture<ResponseBody<ArrayList<DepartmentView>>> response;
        try {
            response = students.listDepartments(true);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> {
            if (disposed || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                replaceItems(department, body.data());
            }
        }));
    }

    private void loadMajors(String departmentId) {
        long generation = requestGeneration.get();
        CompletableFuture<ResponseBody<ArrayList<MajorView>>> response;
        try {
            response = students.listMajors(departmentId);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        major.removeAllItems();
        major.addItem(null);
        classBox.removeAllItems();
        classBox.addItem(null);
        response.whenComplete((body, failure) -> onEdt(() -> {
            if (disposed || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                replaceItems(major, body.data());
            }
        }));
    }

    private void loadClasses(String majorId) {
        long generation = requestGeneration.get();
        CompletableFuture<ResponseBody<ArrayList<ClassView>>> response;
        try {
            response = students.listClasses(majorId);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        classBox.removeAllItems();
        classBox.addItem(null);
        response.whenComplete((body, failure) -> onEdt(() -> {
            if (disposed || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                replaceItems(classBox, body.data());
            }
        }));
    }

    @SuppressWarnings("unchecked")
    private static <T> void replaceItems(JComboBox<T> combo, List<T> items) {
        combo.removeAllItems();
        combo.addItem(null);
        for (T item : items) combo.addItem(item);
    }

    private void submit() {
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

    private void showSuccess(StudentAdmissionResult result) {
        JPanel success = new JPanel(new GridBagLayout());
        success.setBackground(UiColors.BACKGROUND_PAGE);
        success.setBorder(UiBorders.pageInset());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 0;
        JLabel title = new JLabel("录取成功");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.SUCCESS_FG);
        success.add(title, constraints);
        constraints.gridy = 1;
        constraints.insets = new Insets(UiSpacing.SPACE_4, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        JLabel campusCardLabel = new JLabel("一卡通号: " + result.campusCardNumber());
        campusCardLabel.setName("student.admission.success.campus-card");
        campusCardLabel.setFont(UiTypography.SECTION_TITLE);
        campusCardLabel.setForeground(UiColors.TEXT_PRIMARY);
        success.add(campusCardLabel, constraints);
        constraints.gridy = 2;
        JLabel studentNumberLabel = new JLabel("学号: " + result.studentNumber());
        studentNumberLabel.setName("student.admission.success.student-number");
        studentNumberLabel.setFont(UiTypography.SECTION_TITLE);
        studentNumberLabel.setForeground(UiColors.TEXT_PRIMARY);
        success.add(studentNumberLabel, constraints);
        constraints.gridy = 3;
        JLabel hintLabel = new JLabel("初始密码为 12345678，请首次登录后修改");
        hintLabel.setName("student.admission.success.password-hint");
        hintLabel.setFont(UiTypography.BODY);
        hintLabel.setForeground(UiColors.TEXT_SECONDARY);
        success.add(hintLabel, constraints);
        constraints.gridy = 4;
        constraints.insets = new Insets(UiSpacing.SPACE_6, 0, 0, 0);
        JButton close = new JButton("确定");
        close.setName("student.admission.success.close");
        close.setFont(UiTypography.BODY);
        close.getAccessibleContext().setAccessibleName("确定");
        close.addActionListener(event -> dispose());
        success.add(close, constraints);
        setContentPane(success);
        revalidate();
        repaint();
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

    @Override public void dispose() {
        disposed = true;
        requestGeneration.incrementAndGet();
        super.dispose();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String safeMessage(ResponseBody<?> body, String fallback) {
        return body != null && body.message() != null && !body.message().isBlank() ? body.message() : fallback;
    }

    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    private void establishInitialFocus() {
        SwingUtilities.invokeLater(() -> {
            if (isShowing() && !initialFocusEstablished) studentName.requestFocusInWindow();
        });
    }

    private static JTextField textField(String name, String accessibleName) {
        JTextField result = new JTextField(24);
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.setBorder(UiBorders.LINE);
        result.getAccessibleContext().setAccessibleName(accessibleName);
        return result;
    }

    private static JComboBox<String> comboBox(String name, String accessibleName, String[] items) {
        JComboBox<String> result = new JComboBox<>(items);
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.getAccessibleContext().setAccessibleName(accessibleName);
        return result;
    }

    private final class AdmissionFocusTraversalPolicy extends FocusTraversalPolicy {
        private List<Component> order() {
            List<Component> components = new ArrayList<>();
            addIfEligible(components, studentName);
            addIfEligible(components, gender);
            addIfEligible(components, studentType);
            addIfEligible(components, department);
            addIfEligible(components, major);
            addIfEligible(components, classBox);
            addIfEligible(components, year);
            addIfEligible(components, email);
            addIfEligible(components, phone);
            addIfEligible(components, cancel);
            addIfEligible(components, submit);
            return components;
        }

        private static void addIfEligible(List<Component> components, Component component) {
            if (component.isVisible() && component.isEnabled() && component.isFocusable()) components.add(component);
        }

        @Override public Component getComponentAfter(Container root, Component current) {
            List<Component> components = order();
            if (components.isEmpty()) return null;
            int index = components.indexOf(current);
            return components.get(index < 0 ? 0 : (index + 1) % components.size());
        }
        @Override public Component getComponentBefore(Container root, Component current) {
            List<Component> components = order();
            if (components.isEmpty()) return null;
            int index = components.indexOf(current);
            return components.get(index < 0 ? components.size() - 1 : (index - 1 + components.size()) % components.size());
        }
        @Override public Component getFirstComponent(Container root) { List<Component> components = order(); return components.isEmpty() ? null : components.getFirst(); }
        @Override public Component getLastComponent(Container root) { List<Component> components = order(); return components.isEmpty() ? null : components.getLast(); }
        @Override public Component getDefaultComponent(Container root) { return getFirstComponent(root); }
    }
}
