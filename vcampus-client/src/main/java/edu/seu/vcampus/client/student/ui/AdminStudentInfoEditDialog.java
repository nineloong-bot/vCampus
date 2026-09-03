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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Modal editor for admin to edit all academic fields of a student record. */
public final class AdminStudentInfoEditDialog extends JDialog {
    private static final Border SUBMIT_BORDER = BorderFactory.createCompoundBorder(UiBorders.LINE,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2, UiSpacing.SPACE_4,
                    UiSpacing.SPACE_2, UiSpacing.SPACE_4));
    private static final Border SUBMIT_FOCUS_BORDER = BorderFactory.createCompoundBorder(UiBorders.FOCUS,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1,
                    UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1));

    private final StudentClientService students;
    private final Consumer<StudentView> saved;
    private final AtomicLong requestGeneration = new AtomicLong();
    private final AtomicLong hierarchyGeneration = new AtomicLong();
    private final JTextField studentNumberField = field("student.info.studentNumber", "学号");
    private final JComboBox<String> studentTypeCombo = new JComboBox<>(
            new String[]{"本科生", "硕士生", "博士生"});
    private final JComboBox<Object> departmentCombo = new JComboBox<>();
    private final JComboBox<Object> majorCombo = new JComboBox<>();
    private final JComboBox<Object> classCombo = new JComboBox<>();
    private final JComboBox<String> statusCombo = new JComboBox<>(
            new String[]{"正常", "休学", "已毕业", "已退学"});
    private final JComboBox<String> enrolledCombo = new JComboBox<>(new String[]{"是", "否"});
    private final JComboBox<String> onCampusCombo = new JComboBox<>(new String[]{"是", "否"});
    private final JTextField campusField = field("student.info.campus", "校区");
    private final JTextField educationLevelField = field("student.info.educationLevel", "培养层次");
    private final JTextField trainingModeField = field("student.info.trainingMode", "培养方式");
    private final JTextField programLengthField = field("student.info.programLength", "学制（年）");
    private final JComboBox<String> attendanceModeCombo = new JComboBox<>(
            new String[]{"住校", "走读", "借宿", "其他"});
    private final JTextField degreeNameField = field("student.info.degreeName", "就读学位");
    private final JTextField educationNameField = field("student.info.educationName", "就读学历");
    private final JTextField expectedGraduationField = field("student.info.expectedGraduation", "预计毕业日期");
    private final JTextField graduationField = field("student.info.graduation", "毕业日期");
    private final JTextField studentSourceField = field("student.info.studentSource", "学生来源");
    private final JTextField graduateStudyModeField = field("student.info.graduateStudyMode", "学习形式（研）");
    private final JTextField counselorNameField = field("student.info.counselorName", "辅导员姓名");
    private final JTextField counselorContactField = field("student.info.counselorContact", "辅导员联系方式");
    private final JTextField reasonField = field("student.info.reason", "变更原因");
    private final JLabel error = label("student.info.error", "提示");
    private final JButton refresh = button("刷新数据", "student.info.refresh");
    private final JButton cancel = button("取消", "student.info.cancel");
    private final JButton submit = button("保存", "student.info.submit");
    private StudentView base;
    private StudentAcademicProfile academic;
    private boolean conflict;
    private boolean disposed;
    private boolean published;
    private boolean suppressComboEvents;

    public AdminStudentInfoEditDialog(Window owner, StudentClientService students,
                                      StudentView initial, StudentAcademicProfile academic,
                                      Consumer<StudentView> saved) {
        super(owner, "编辑学籍信息", ModalityType.APPLICATION_MODAL);
        this.students = Objects.requireNonNull(students, "students");
        this.base = Objects.requireNonNull(initial, "initial");
        this.academic = Objects.requireNonNull(academic, "academic");
        this.saved = Objects.requireNonNull(saved, "saved");
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

    private void styleCombo(JComboBox<String> combo, String name, String accessibleName) {
        combo.setName(name);
        combo.setFont(UiTypography.BODY);
        combo.getAccessibleContext().setAccessibleName(accessibleName);
    }

    private JPanel buildForm() {
        JPanel content = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        content.setBackground(UiColors.BACKGROUND_PAGE);
        content.setBorder(UiBorders.pageInset());
        JLabel title = new JLabel("编辑学籍信息");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        content.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        c.anchor = GridBagConstraints.WEST;

        addField(form, c, "学号", studentNumberField, 0);
        addComboField(form, c, "学生类别", studentTypeCombo, 1);

        Dimension comboSize = new Dimension(200, Math.max(32, studentNumberField.getPreferredSize().height));
        for (JComboBox<?> combo : new JComboBox<?>[]{departmentCombo, majorCombo, classCombo}) {
            combo.setFont(UiTypography.BODY);
            combo.setPreferredSize(comboSize);
        }
        departmentCombo.setName("student.info.department");
        departmentCombo.getAccessibleContext().setAccessibleName("院系");
        departmentCombo.addItem("全部");
        majorCombo.setName("student.info.major");
        majorCombo.getAccessibleContext().setAccessibleName("专业");
        majorCombo.addItem("全部");
        classCombo.setName("student.info.class");
        classCombo.getAccessibleContext().setAccessibleName("班级");
        classCombo.addItem("全部");

        addComboField(form, c, "院系", departmentCombo, 2);
        addComboField(form, c, "专业", majorCombo, 3);
        addComboField(form, c, "班级", classCombo, 4);
        addComboField(form, c, "学籍状态", statusCombo, 5);
        addComboField(form, c, "是否在籍", enrolledCombo, 6);
        addComboField(form, c, "是否在校", onCampusCombo, 7);
        addField(form, c, "校区", campusField, 8);
        addField(form, c, "培养层次", educationLevelField, 9);
        addField(form, c, "培养方式", trainingModeField, 10);
        addField(form, c, "学制（年）", programLengthField, 11);
        addComboField(form, c, "就读方式", attendanceModeCombo, 12);
        addField(form, c, "就读学位", degreeNameField, 13);
        addField(form, c, "就读学历", educationNameField, 14);
        addField(form, c, "预计毕业日期", expectedGraduationField, 15);
        addField(form, c, "毕业日期", graduationField, 16);
        addField(form, c, "学生来源", studentSourceField, 17);
        addField(form, c, "学习形式（研）", graduateStudyModeField, 18);
        addField(form, c, "辅导员姓名", counselorNameField, 19);
        addField(form, c, "辅导员联系方式", counselorContactField, 20);
        addField(form, c, "变更原因", reasonField, 21);

        JScrollPane scroll = new JScrollPane(form,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        content.add(scroll, BorderLayout.CENTER);

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

    private void addField(JPanel form, GridBagConstraints c, String title, JTextField value, int row) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(title);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        form.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        form.add(value, c);
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    private void addComboField(JPanel form, GridBagConstraints c, String title, JComboBox<?> combo, int row) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(title);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        form.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        form.add(combo, c);
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    private void initComboListeners() {
        departmentCombo.addActionListener(e -> {
            if (suppressComboEvents) return;
            Object item = departmentCombo.getSelectedItem();
            String id = (item instanceof DepartmentView d) ? d.departmentId() : null;
            cascadeLoadMajors(id);
        });
        majorCombo.addActionListener(e -> {
            if (suppressComboEvents) return;
            Object item = majorCombo.getSelectedItem();
            String id = (item instanceof MajorView m) ? m.majorId() : null;
            cascadeLoadClasses(id);
        });
    }

    private void loadDepartments() {
        loadDepartments(ignored -> {});
    }

    private void loadDepartments(Consumer<Boolean> completed) {
        long generation = hierarchyGeneration.incrementAndGet();
        students.listDepartments(false).whenComplete((body, failure) -> onEdt(() -> {
            if (disposed || generation != hierarchyGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                ArrayList<DepartmentView> items = body.data();
                Object[] comboItems = new Object[items.size() + 1];
                comboItems[0] = "全部";
                for (int i = 0; i < items.size(); i++) comboItems[i + 1] = items.get(i);
                suppressComboEvents = true;
                try {
                    departmentCombo.setModel(new DefaultComboBoxModel<>(comboItems));
                    if (base.departmentName() != null) {
                        for (int i = 1; i < comboItems.length; i++) {
                            if (comboItems[i] instanceof DepartmentView d
                                    && base.departmentName().equals(d.name())) {
                                departmentCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                } finally { suppressComboEvents = false; }
                Object selected = departmentCombo.getSelectedItem();
                if (selected instanceof DepartmentView d) cascadeLoadMajors(d.departmentId(), completed);
                else completed.accept(false);
            } else completed.accept(false);
        }));
    }

    private void cascadeLoadMajors(String departmentId) {
        cascadeLoadMajors(departmentId, ignored -> {});
    }

    private void cascadeLoadMajors(String departmentId, Consumer<Boolean> completed) {
        long generation = hierarchyGeneration.incrementAndGet();
        suppressComboEvents = true;
        try {
            majorCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"正在加载..."}));
            classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
        } finally { suppressComboEvents = false; }
        if (departmentId == null) {
            suppressComboEvents = true;
            try { majorCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"})); }
            finally { suppressComboEvents = false; }
            completed.accept(false);
            return;
        }
        students.listMajors(departmentId, false).whenComplete((body, failure) -> onEdt(() -> {
            if (disposed || generation != hierarchyGeneration.get()
                    || !departmentId.equals(selectedDepartmentId())) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                ArrayList<MajorView> items = body.data();
                Object[] comboItems = new Object[items.size() + 1];
                comboItems[0] = "全部";
                for (int i = 0; i < items.size(); i++) comboItems[i + 1] = items.get(i);
                suppressComboEvents = true;
                try {
                    majorCombo.setModel(new DefaultComboBoxModel<>(comboItems));
                    if (base.majorId() != null) {
                        for (int i = 1; i < comboItems.length; i++) {
                            if (comboItems[i] instanceof MajorView m
                                    && base.majorId().equals(m.majorId())) {
                                majorCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                } finally { suppressComboEvents = false; }
                Object selected = majorCombo.getSelectedItem();
                if (selected instanceof MajorView m) cascadeLoadClasses(m.majorId(), completed);
                else completed.accept(false);
            } else {
                suppressComboEvents = true;
                try { majorCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"})); }
                finally { suppressComboEvents = false; }
                completed.accept(false);
            }
        }));
    }

    private void cascadeLoadClasses(String majorId) {
        cascadeLoadClasses(majorId, ignored -> {});
    }

    private void cascadeLoadClasses(String majorId, Consumer<Boolean> completed) {
        long generation = hierarchyGeneration.incrementAndGet();
        suppressComboEvents = true;
        try { classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"正在加载..."})); }
        finally { suppressComboEvents = false; }
        if (majorId == null) {
            suppressComboEvents = true;
            try { classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"})); }
            finally { suppressComboEvents = false; }
            completed.accept(false);
            return;
        }
        students.listClasses(majorId, false).whenComplete((body, failure) -> onEdt(() -> {
            if (disposed || generation != hierarchyGeneration.get()
                    || !majorId.equals(selectedMajorId())) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                ArrayList<ClassView> items = body.data();
                Object[] comboItems = new Object[items.size() + 1];
                comboItems[0] = "全部";
                for (int i = 0; i < items.size(); i++) comboItems[i + 1] = items.get(i);
                suppressComboEvents = true;
                try {
                    classCombo.setModel(new DefaultComboBoxModel<>(comboItems));
                    if (base.classId() != null) {
                        for (int i = 1; i < comboItems.length; i++) {
                            if (comboItems[i] instanceof ClassView cv
                                    && base.classId().equals(cv.classId())) {
                                classCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                } finally { suppressComboEvents = false; }
                completed.accept(base.classId() != null && base.classId().equals(selectedClassId()));
            } else {
                suppressComboEvents = true;
                try { classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"})); }
                finally { suppressComboEvents = false; }
                completed.accept(false);
            }
        }));
    }

    private void save() {
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
        try { response = students.get(base.studentId()); }
        catch (RuntimeException failure) { response = CompletableFuture.failedFuture(failure); }
        response.whenComplete((body, failure) -> onEdt(() -> finishRefresh(generation, body, failure)));
    }

    private void finishRefresh(long generation, ResponseBody<StudentView> body, Throwable failure) {
        if (disposed || generation != requestGeneration.get()) return;
        refresh.setText("刷新数据");
        if (failure == null && body != null && body.success() && body.data() != null) {
            base = body.data();
            conflict = true;
            refresh.setVisible(true);
            refresh.setEnabled(false);
            submit.setEnabled(false);
            studentNumberField.setText(base.studentNumber());
            studentTypeCombo.setSelectedItem(studentTypeLabel(base.studentType()));
            statusCombo.setSelectedItem(statusLabel(base.status()));
            error.setText("正在重新绑定院系、专业和班级……");
            loadDepartments(success -> finishHierarchyRefresh(generation, success));
            return;
        }
        refresh.setEnabled(true);
        error.setText(failure == null ? safeMessage(body, "刷新失败，请稍后重试") : "刷新失败，请稍后重试");
    }

    private void finishHierarchyRefresh(long generation, boolean success) {
        if (disposed || generation != requestGeneration.get()) return;
        refresh.setText("刷新数据");
        if (success) {
            conflict = false;
            refresh.setVisible(false);
            refresh.setEnabled(true);
            submit.setEnabled(true);
            error.setText("数据已刷新，请确认后保存");
            return;
        }
        conflict = true;
        refresh.setVisible(true);
        refresh.setEnabled(true);
        submit.setEnabled(false);
        error.setText("院系、专业或班级刷新失败，请重试");
    }

    private void setSaving(boolean saving) {
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

    @Override public void dispose() {
        disposed = true;
        requestGeneration.incrementAndGet();
        hierarchyGeneration.incrementAndGet();
        super.dispose();
    }

    private String selectedDepartmentId() {
        Object selected = departmentCombo.getSelectedItem();
        return selected instanceof DepartmentView value ? value.departmentId() : null;
    }

    private String selectedMajorId() {
        Object selected = majorCombo.getSelectedItem();
        return selected instanceof MajorView value ? value.majorId() : null;
    }

    private String selectedClassId() {
        Object selected = classCombo.getSelectedItem();
        return selected instanceof ClassView value ? value.classId() : null;
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

    private static String statusLabel(StudentStatus status) {
        if (status == null) return "正常";
        return switch (status) {
            case ACTIVE -> "正常";
            case SUSPENDED -> "休学";
            case GRADUATED -> "已毕业";
            case WITHDRAWN -> "已退学";
        };
    }

    private static StudentStatus parseStatus(String label) {
        if (label == null) return null;
        return switch (label) {
            case "正常" -> StudentStatus.ACTIVE;
            case "休学" -> StudentStatus.SUSPENDED;
            case "已毕业" -> StudentStatus.GRADUATED;
            case "已退学" -> StudentStatus.WITHDRAWN;
            default -> null;
        };
    }

    private static String studentTypeLabel(StudentType type) {
        if (type == null) return "本科生";
        return switch (type) {
            case UNDERGRADUATE -> "本科生";
            case MASTER -> "硕士生";
            case DOCTORATE -> "博士生";
        };
    }

    private static StudentType parseStudentType(String label) {
        if (label == null) return null;
        return switch (label) {
            case "本科生" -> StudentType.UNDERGRADUATE;
            case "硕士生" -> StudentType.MASTER;
            case "博士生" -> StudentType.DOCTORATE;
            default -> null;
        };
    }

    private static Boolean parseYesNo(String label) {
        return label == null ? null : "是".equals(label);
    }

    private static Integer parseProgramLength(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) return null;
        try { return Integer.parseInt(trimmed); }
        catch (NumberFormatException failure) { return null; }
    }

    private static LocalDate parseDate(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) return null;
        try { return LocalDate.parse(trimmed); }
        catch (DateTimeParseException failure) { return null; }
    }
}
