package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Identity fields used by the administrator-only manual student workflow. */
final class ManualStudentFieldsPanel extends JPanel {
    private final Map<String, JComponent> inputs = new LinkedHashMap<>();
    private final JTextField campusCard = field("campusCardNumber",
            "9 位数字；第 2 位对应学生类型，第 4–5 位是入学年份，例：213240099");
    private final JTextField studentNumber = field("studentNumber",
            "专业 3 位编码+年份 2 位+班号 1 位+序号 2 位，例：09024199");
    private final JTextField studentName = field("studentName", "2–64 个字符，例：李雷");
    private final JComboBox<String> gender = combo("gender", new String[]{"", "男", "女"},
            "必填，请选择男或女");
    private final JComboBox<String> studentType = combo("studentType",
            new String[]{"", "本科生", "硕士生", "博士生"}, "必填，与一卡通号第 2 位一致");
    private final JComboBox<String> documentType = combo("idDocumentType",
            new String[]{"", "居民身份证", "护照", "港澳台居民居住证", "其他"}, "必填，请选择证件类型");
    private final JTextField documentNumber = field("idDocumentNumber",
            "居民身份证为 18 位，最后一位可为 X，例：11010519491231002X");
    private final JTextField birthDate = field("birthDate", "格式 yyyy-MM-dd，应与身份证出生日期一致");
    private final JTextField enrollmentDate = field("enrollmentDate",
            "格式 yyyy-MM-dd；入学时必须已年满 18 周岁，例：2024-09-01");
    private final ClassView studentClass;

    ManualStudentFieldsPanel(DepartmentView department, MajorView major, ClassView studentClass) {
        super(new GridBagLayout());
        this.studentClass = studentClass;
        setOpaque(false);
        enrollmentDate.setText(studentClass.enrollmentYear() + "-09-01");
        int row = 0;
        row(row++, "所属学院", derived("department", department.code() + " - " + department.name()), false);
        row(row++, "所属专业", derived("major", major.code() + " - " + major.name()), false);
        row(row++, "所属班级", derived("class", studentClass.code() + " - " + studentClass.name()), false);
        row(row++, "一卡通号", campusCard, true); row(row++, "学号", studentNumber, true);
        row(row++, "姓名", studentName, true); row(row++, "性别", gender, true);
        row(row++, "学生类型", studentType, true); row(row++, "证件类型", documentType, true);
        row(row++, "证件号码", documentNumber, true); row(row++, "出生日期", birthDate, true);
        row(row, "入学日期", enrollmentDate, true);
    }

    CreateStudentManualCommand command() {
        return new CreateStudentManualCommand(text(campusCard), text(studentNumber), text(studentName),
                selected(gender), type(), selected(documentType), text(documentNumber), date(birthDate),
                date(enrollmentDate), studentClass.classId());
    }

    JComponent input(String key) { return inputs.get(key); }
    void setInputsEnabled(boolean enabled) { inputs.values().forEach(input -> input.setEnabled(enabled)); }
    boolean isDirty() {
        return inputs.entrySet().stream().anyMatch(entry -> !"enrollmentDate".equals(entry.getKey())
                && hasValue(entry.getValue()));
    }

    private static boolean hasValue(JComponent value) {
        if (value instanceof JTextField field) return !field.getText().isBlank();
        return value instanceof JComboBox<?> combo && combo.getSelectedIndex() > 0;
    }
    private JTextField field(String key, String tooltip) {
        JTextField value = new JTextField(24); configure(value, key, tooltip); return value;
    }
    private JComboBox<String> combo(String key, String[] values, String tooltip) {
        JComboBox<String> value = new JComboBox<>(values); configure(value, key, tooltip); return value;
    }
    private void configure(JComponent value, String key, String tooltip) {
        value.setName("student.manual." + key); value.setToolTipText(tooltip); inputs.put(key, value);
    }
    private static JLabel derived(String key, String value) {
        JLabel label = new JLabel(value); label.setName("student.manual." + key);
        label.setFont(UiTypography.BODY); label.setForeground(UiColors.TEXT_SECONDARY); return label;
    }
    private void row(int row, String title, Component input, boolean required) {
        GridBagConstraints label = new GridBagConstraints(); label.gridx = 0; label.gridy = row;
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        JLabel text = new JLabel((required ? "* " : "") + title); text.setFont(UiTypography.CAPTION);
        text.setForeground(required ? UiColors.TEXT_PRIMARY : UiColors.TEXT_SECONDARY); add(text, label);
        GridBagConstraints field = new GridBagConstraints(); field.gridx = 1; field.gridy = row;
        field.weightx = 1; field.fill = GridBagConstraints.HORIZONTAL;
        field.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0); add(input, field);
    }
    private StudentType type() {
        return switch (studentType.getSelectedIndex()) {
            case 1 -> StudentType.UNDERGRADUATE; case 2 -> StudentType.MASTER;
            case 3 -> StudentType.DOCTORATE; default -> null;
        };
    }
    private static String selected(JComboBox<String> box) {
        Object value = box.getSelectedItem(); return value == null || value.toString().isBlank()
                ? null : value.toString();
    }
    private static String text(JTextField field) {
        String value = field.getText(); return value == null || value.isBlank() ? null : value.trim();
    }
    private static LocalDate date(JTextField field) {
        String value = text(field); return value == null ? null : LocalDate.parse(value);
    }
}
