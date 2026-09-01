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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class EnrollmentChangeDialog extends JDialog {
    private static final Border SUBMIT_BORDER = BorderFactory.createCompoundBorder(UiBorders.LINE,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2, UiSpacing.SPACE_4,
                    UiSpacing.SPACE_2, UiSpacing.SPACE_4));
    private static final Border SUBMIT_FOCUS_BORDER = BorderFactory.createCompoundBorder(UiBorders.FOCUS,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1,
                    UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1));

    private final StudentClientService students;
    private final Consumer<StudentView> saved;
    private final AtomicLong requestGeneration = new AtomicLong();
    private final JComboBox<DepartmentView> departmentCombo = combo("student.enrollment.department");
    private final JComboBox<MajorView> majorCombo = combo("student.enrollment.major");
    private final JComboBox<ClassView> classCombo = combo("student.enrollment.class");
    private final JTextField effectiveDateField = field("student.enrollment.effective-date", "生效日期");
    private final JTextField reasonField = field("student.enrollment.reason", "变更原因");
    private final JLabel error = label("student.enrollment.error", "变更班级提示");
    private final JButton refresh = button("刷新数据", "student.enrollment.refresh");
    private final JButton cancel = button("取消", "student.enrollment.cancel");
    private final JButton submit = button("提交变更", "student.enrollment.submit");
    private final JLabel studentNameLabel = label("student.enrollment.student-name", "学生姓名");
    private final JLabel currentClassLabel = label("student.enrollment.current-class", "当前班级");
    private StudentView base;
    private boolean conflict;
    private boolean disposed;
    private boolean published;
    private boolean suppressingEvents;

    public EnrollmentChangeDialog(Window owner, StudentClientService students,
                                  StudentView initial, Consumer<StudentView> saved) {
        super(owner, "变更班级", ModalityType.APPLICATION_MODAL);
        this.students = Objects.requireNonNull(students, "students");
        this.base = Objects.requireNonNull(initial, "initial");
        this.saved = Objects.requireNonNull(saved, "saved");
        studentNameLabel.setText(initial.studentName());
        currentClassLabel.setText(initial.classId());
        effectiveDateField.setText(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildForm());
        refresh.setVisible(false);
        refresh.addActionListener(event -> refreshBase());
        cancel.addActionListener(event -> dispose());
        submit.addActionListener(event -> save());
        getRootPane().setDefaultButton(submit);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new EnrollmentFocusTraversalPolicy());
        setSize(new Dimension(640, 520));
        setResizable(false);
        setLocationRelativeTo(owner);
        setupCascading();
        loadDepartments();
    }

    private JPanel buildForm() {
        JPanel content = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        content.setBackground(UiColors.BACKGROUND_PAGE);
        content.setBorder(UiBorders.pageInset());
        JLabel title = new JLabel("变更班级");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        content.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        constraints.anchor = GridBagConstraints.WEST;
        addReadonly(form, constraints, "学生姓名", studentNameLabel, 0);
        addReadonly(form, constraints, "当前班级", currentClassLabel, 1);
        addCombo(form, constraints, "目标院系", departmentCombo, 2);
        addCombo(form, constraints, "目标专业", majorCombo, 3);
        addCombo(form, constraints, "目标班级", classCombo, 4);
        addField(form, constraints, "生效日期", effectiveDateField, 5);
        addField(form, constraints, "变更原因", reasonField, 6);
        content.add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, UiSpacing.SPACE_2));
        bottom.setOpaque(false);
        error.setForeground(UiColors.ERROR_FG);
        error.setFont(UiTypography.CAPTION);
        bottom.add(error, BorderLayout.NORTH);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        left.setOpaque(false);
        left.add(cancel);
        left.add(refresh);
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
        submit.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { submit.setBorder(SUBMIT_FOCUS_BORDER); }
            @Override public void focusLost(FocusEvent event) { submit.setBorder(SUBMIT_BORDER); }
        });
        right.add(submit);
        bottom.add(right, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);
        return content;
    }

    private static void addReadonly(JPanel form, GridBagConstraints constraints,
                                    String title, JLabel value, int row) {
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
        value.setFont(UiTypography.BODY);
        value.setForeground(UiColors.TEXT_PRIMARY);
        form.add(value, constraints);
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    private static void addCombo(JPanel form, GridBagConstraints constraints,
                                 String title, JComboBox<?> value, int row) {
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
        form.add(value, constraints);
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    private static void addField(JPanel form, GridBagConstraints constraints,
                                 String title, JTextField value, int row) {
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
        form.add(value, constraints);
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    private void setupCascading() {
        departmentCombo.addActionListener(event -> {
            if (suppressingEvents) return;
            DepartmentView selected = (DepartmentView) departmentCombo.getSelectedItem();
            if (selected == null) { majorCombo.removeAllItems(); classCombo.removeAllItems(); return; }
            loadMajors(selected.departmentId());
        });
        majorCombo.addActionListener(event -> {
            if (suppressingEvents) return;
            MajorView selected = (MajorView) majorCombo.getSelectedItem();
            if (selected == null) { classCombo.removeAllItems(); return; }
            loadClasses(selected.majorId());
        });
    }

    private void loadDepartments() {
        suppressingEvents = true;
        departmentCombo.removeAllItems();
        CompletableFuture<ResponseBody<ArrayList<DepartmentView>>> response;
        try {
            response = students.listDepartments(true);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> {
            if (disposed) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                for (DepartmentView dept : body.data()) departmentCombo.addItem(dept);
            }
            suppressingEvents = false;
        }));
    }

    void loadMajors(String departmentId) {
        suppressingEvents = true;
        majorCombo.removeAllItems();
        classCombo.removeAllItems();
        suppressingEvents = false;
        CompletableFuture<ResponseBody<ArrayList<MajorView>>> response;
        try {
            response = students.listMajors(departmentId);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> {
            if (disposed) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                for (MajorView major : body.data()) majorCombo.addItem(major);
            }
        }));
    }

    private void loadClasses(String majorId) {
        suppressingEvents = true;
        classCombo.removeAllItems();
        suppressingEvents = false;
        CompletableFuture<ResponseBody<ArrayList<ClassView>>> response;
        try {
            response = students.listClasses(majorId);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> {
            if (disposed) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                for (ClassView cls : body.data()) classCombo.addItem(cls);
            }
        }));
    }

    private void save() {
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

    private void refreshBase() {
        if (disposed || !conflict) return;
        long generation = requestGeneration.incrementAndGet();
        refresh.setEnabled(false);
        refresh.setText("正在刷新");
        CompletableFuture<ResponseBody<StudentView>> response;
        try {
            response = students.get(base.studentId());
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> finishRefresh(generation, body, failure)));
    }

    private void finishRefresh(long generation, ResponseBody<StudentView> body, Throwable failure) {
        if (disposed || generation != requestGeneration.get()) return;
        refresh.setText("刷新数据");
        if (failure == null && body != null && body.success() && body.data() != null) {
            base = body.data();
            conflict = false;
            refresh.setVisible(false);
            refresh.setEnabled(true);
            submit.setEnabled(true);
            currentClassLabel.setText(base.classId());
            error.setText("数据已刷新，请确认后保存");
            return;
        }
        refresh.setEnabled(true);
        error.setText(failure == null ? safeMessage(body, "刷新失败，请稍后重试") : "刷新失败，请稍后重试");
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

    @SuppressWarnings("unchecked")
    private static <E> JComboBox<E> combo(String name) {
        JComboBox<E> result = new JComboBox<>();
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.setBorder(UiBorders.LINE);
        result.getAccessibleContext().setAccessibleName(name);
        return result;
    }

    private static JTextField field(String name, String accessibleName) {
        JTextField result = new JTextField(24);
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.setBorder(UiBorders.LINE);
        result.getAccessibleContext().setAccessibleName(accessibleName);
        return result;
    }

    private static JButton button(String title, String name) {
        JButton result = new JButton(title);
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.getAccessibleContext().setAccessibleName(title);
        return result;
    }

    private static JLabel label(String name, String accessibleName) {
        JLabel result = new JLabel(" ");
        result.setName(name);
        result.getAccessibleContext().setAccessibleName(accessibleName);
        return result;
    }

    private final class EnrollmentFocusTraversalPolicy extends FocusTraversalPolicy {
        private List<Component> order() {
            List<Component> components = new ArrayList<>();
            addIfEligible(components, departmentCombo);
            addIfEligible(components, majorCombo);
            addIfEligible(components, classCombo);
            addIfEligible(components, effectiveDateField);
            addIfEligible(components, reasonField);
            addIfEligible(components, cancel);
            addIfEligible(components, refresh);
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
