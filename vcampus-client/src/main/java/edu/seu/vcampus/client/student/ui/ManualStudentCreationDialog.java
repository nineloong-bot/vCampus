package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import java.awt.*;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Strict administrator-only UI for creating a student with supplied identifiers. */
public final class ManualStudentCreationDialog extends JDialog {
    private final StudentClientService students;
    private final ClassView studentClass;
    private final AtomicLong generation = new AtomicLong();
    private final Map<String, JComponent> inputs = new LinkedHashMap<>();
    private final JTextField campusCard = field("campusCardNumber",
            "9 位数字；第 2 位对应学生类型，第 4–5 位是入学年份，例：213240099");
    private final JTextField studentNumber = field("studentNumber",
            "专业 3 位编码+年份 2 位+班号 1 位+序号 2 位，例：09024199");
    private final JTextField studentName = field("studentName", "2–64 个字符，例：李雷");
    private final JComboBox<String> gender = combo("gender", new String[]{"", "男", "女"}, "必填，请选择男或女");
    private final JComboBox<String> studentType = combo("studentType",
            new String[]{"", "本科生", "硕士生", "博士生"}, "必填，与一卡通号第 2 位一致");
    private final JComboBox<String> documentType = combo("idDocumentType",
            new String[]{"", "居民身份证", "护照", "港澳台居民居住证", "其他"}, "必填，请选择证件类型");
    private final JTextField documentNumber = field("idDocumentNumber",
            "居民身份证为 18 位，最后一位可为 X，例：11010519491231002X");
    private final JTextField birthDate = field("birthDate", "格式 yyyy-MM-dd，应与身份证出生日期一致");
    private final JTextField enrollmentDate = field("enrollmentDate",
            "格式 yyyy-MM-dd；入学时必须已年满 18 周岁，例：2024-09-01");
    private final JLabel error = new JLabel(" ");
    private final JButton submit = new JButton("新增学生");
    private boolean disposed;

    public ManualStudentCreationDialog(Window owner, StudentClientService students,
            DepartmentView department, MajorView major, ClassView studentClass) {
        super(owner, "手动新增学生", ModalityType.APPLICATION_MODAL);
        this.students = Objects.requireNonNull(students);
        this.studentClass = Objects.requireNonNull(studentClass);
        Objects.requireNonNull(department);
        Objects.requireNonNull(major);
        enrollmentDate.setText(studentClass.enrollmentYear() + "-09-01");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(build(department, major));
        setSize(700, 680);
        setLocationRelativeTo(owner);
    }

    private JPanel build(DepartmentView department, MajorView major) {
        JPanel page = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        page.setBackground(UiColors.BACKGROUND_PAGE);
        page.setBorder(UiBorders.pageInset());
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, UiSpacing.SPACE_1));
        heading.setOpaque(false);
        JLabel title = new JLabel("手动新增学生");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        heading.add(title);
        JLabel hint = new JLabel("初始密码为 12345678，邮箱和手机号由学生首次登录后补充");
        hint.setFont(UiTypography.CAPTION);
        hint.setForeground(UiColors.TEXT_SECONDARY);
        heading.add(hint);
        page.add(heading, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        int row = 0;
        row(form, row++, "所属学院", derived("department", department.code() + " - " + department.name()), false);
        row(form, row++, "所属专业", derived("major", major.code() + " - " + major.name()), false);
        row(form, row++, "所属班级", derived("class", studentClass.code() + " - " + studentClass.name()), false);
        row(form, row++, "一卡通号", campusCard, true);
        row(form, row++, "学号", studentNumber, true);
        row(form, row++, "姓名", studentName, true);
        row(form, row++, "性别", gender, true);
        row(form, row++, "学生类型", studentType, true);
        row(form, row++, "证件类型", documentType, true);
        row(form, row++, "证件号码", documentNumber, true);
        row(form, row++, "出生日期", birthDate, true);
        row(form, row, "入学日期", enrollmentDate, true);
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        page.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0));
        bottom.setOpaque(false);
        error.setName("student.manual.error");
        error.setForeground(UiColors.ERROR_FG);
        error.setFont(UiTypography.CAPTION);
        bottom.add(error, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        buttons.setOpaque(false);
        JButton cancel = new JButton("取消");
        cancel.setName("student.manual.cancel");
        cancel.addActionListener(event -> dispose());
        submit.setName("student.manual.submit");
        submit.addActionListener(event -> submit());
        buttons.add(cancel);
        buttons.add(submit);
        bottom.add(buttons, BorderLayout.EAST);
        page.add(bottom, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(submit);
        return page;
    }

    private void submit() {
        CreateStudentManualCommand command;
        try {
            command = new CreateStudentManualCommand(text(campusCard), text(studentNumber),
                    text(studentName), selected(gender), type(), selected(documentType),
                    text(documentNumber), date(birthDate), date(enrollmentDate), studentClass.classId());
        } catch (DateTimeException invalid) {
            error.setText("日期格式必须为 yyyy-MM-dd");
            return;
        }
        List<StudentFieldError> errors = StudentFieldValidator.validateManual(command, LocalDate.now());
        if (!errors.isEmpty()) {
            StudentFieldError first = errors.getFirst();
            error.setText(first.message());
            JComponent input = inputs.get(first.field());
            if (input != null) input.requestFocusInWindow();
            return;
        }
        long current = generation.incrementAndGet();
        setSaving(true);
        CompletableFuture<ResponseBody<StudentAdmissionResult>> response;
        try { response = students.createManual(StudentFieldValidator.normalizeManual(command)); }
        catch (RuntimeException failure) { response = CompletableFuture.failedFuture(failure); }
        response.whenComplete((body, failure) -> SwingUtilities.invokeLater(() -> {
            if (disposed || current != generation.get()) return;
            if (failure != null || body == null || !body.success() || body.data() == null) {
                setSaving(false);
                error.setText(body != null && body.message() != null ? body.message() : "新增失败，请稍后重试");
                return;
            }
            showSuccess(body.data());
        }));
    }

    private void showSuccess(StudentAdmissionResult result) {
        JPanel success = new JPanel(new GridBagLayout());
        success.setBackground(UiColors.BACKGROUND_PAGE);
        success.setBorder(UiBorders.pageInset());
        JLabel message = new JLabel("<html><h2>新增成功</h2><br>一卡通号：" + result.campusCardNumber()
                + "<br>学号：" + result.studentNumber()
                + "<br><br>初始密码：12345678（首次登录必须修改）</html>");
        message.setName("student.manual.success");
        message.setFont(UiTypography.BODY);
        success.add(message);
        setContentPane(success);
        revalidate();
        repaint();
    }

    private void setSaving(boolean saving) {
        for (JComponent input : inputs.values()) input.setEnabled(!saving);
        submit.setEnabled(!saving);
        submit.setText(saving ? "正在新增…" : "新增学生");
        if (saving) error.setText(" ");
    }

    private JTextField field(String key, String tooltip) {
        JTextField value = new JTextField(24);
        configure(value, key, tooltip);
        return value;
    }

    private JComboBox<String> combo(String key, String[] values, String tooltip) {
        JComboBox<String> value = new JComboBox<>(values);
        configure(value, key, tooltip);
        return value;
    }

    private void configure(JComponent value, String key, String tooltip) {
        value.setName("student.manual." + key);
        value.setToolTipText(tooltip);
        inputs.put(key, value);
    }

    private static JLabel derived(String key, String text) {
        JLabel value = new JLabel(text);
        value.setName("student.manual." + key);
        value.setFont(UiTypography.BODY);
        value.setForeground(UiColors.TEXT_SECONDARY);
        return value;
    }

    private static void row(JPanel form, int row, String title, Component input, boolean required) {
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0; label.gridy = row; label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        JLabel text = new JLabel((required ? "* " : "") + title);
        text.setFont(UiTypography.CAPTION);
        text.setForeground(required ? UiColors.TEXT_PRIMARY : UiColors.TEXT_SECONDARY);
        form.add(text, label);
        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 1; field.gridy = row; field.weightx = 1; field.fill = GridBagConstraints.HORIZONTAL;
        field.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        form.add(input, field);
    }

    private StudentType type() {
        return switch (studentType.getSelectedIndex()) {
            case 1 -> StudentType.UNDERGRADUATE;
            case 2 -> StudentType.MASTER;
            case 3 -> StudentType.DOCTORATE;
            default -> null;
        };
    }
    private static String selected(JComboBox<String> box) {
        Object value = box.getSelectedItem();
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
    private static String text(JTextField field) {
        String value = field.getText();
        return value == null || value.isBlank() ? null : value.trim();
    }
    private static LocalDate date(JTextField field) {
        String value = text(field);
        return value == null ? null : LocalDate.parse(value);
    }

    @Override public void dispose() {
        disposed = true;
        generation.incrementAndGet();
        super.dispose();
    }
}
