package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.CreateTermCommand;
import edu.seu.vcampus.common.course.TermView;
import edu.seu.vcampus.common.course.UpdateTermCommand;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;

/** Modal create/edit form for a complete server-authoritative academic term. */
final class TermEditorDialog extends JDialog {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final CourseUiGateway gateway;
    private final TermView existing;
    private final Runnable onSaved;
    private final JTextField code = field("学期代码");
    private final JTextField name = field("学期名称");
    private final JTextField startDate = field("开学日期");
    private final JTextField endDate = field("结束日期");
    private final JTextField enrollmentStart = field("选课开始");
    private final JTextField enrollmentEnd = field("选课结束");
    private final JTextField adjustmentStart = field("退改补开始");
    private final JTextField adjustmentEnd = field("退改补结束");
    private final JComboBox<String> status = new JComboBox<>(new String[]{"PLANNED", "ACTIVE", "CLOSED"});
    private final JLabel error = label(" ", UiColors.ACCENT);
    private final JButton save;

    TermEditorDialog(Window owner, CourseUiGateway gateway, TermView existing, Runnable onSaved) {
        super(owner, existing == null ? "新建学期" : "编辑学期", ModalityType.APPLICATION_MODAL);
        this.gateway = gateway;
        this.existing = existing;
        this.onSaved = onSaved;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, UiSpacing.LG));
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.XL, UiSpacing.XL, UiSpacing.XL, UiSpacing.XL));
        root.add(title(), BorderLayout.NORTH);
        root.add(form(), BorderLayout.CENTER);
        save = AbstractCoursePanel.primary(existing == null ? "创建学期" : "保存修改");
        save.addActionListener(event -> submit());
        root.add(actions(), BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(save);
        if (existing != null) fill(existing);
        setSize(new Dimension(640, 720));
        setLocationRelativeTo(owner);
    }

    private JPanel title() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel heading = label(existing == null ? "新建学期" : "编辑学期", UiColors.TEXT_PRIMARY);
        heading.setFont(UiTypography.PAGE_TITLE);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        panel.add(label("日期格式 yyyy-MM-dd；时间使用北京时间 yyyy-MM-dd HH:mm", UiColors.TEXT_SECONDARY));
        return panel;
    }

    private JPanel form() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(pair("学期代码（必填）", code, "学期名称（必填）", name));
        panel.add(pair("开学日期", startDate, "结束日期", endDate));
        panel.add(pair("选课开始", enrollmentStart, "选课结束", enrollmentEnd));
        panel.add(pair("退改补开始", adjustmentStart, "退改补结束", adjustmentEnd));
        status.setFont(UiTypography.BODY);
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        status.getAccessibleContext().setAccessibleName("学期状态");
        panel.add(pair("学期状态", status, "开放判定",
                label("由学期状态与服务器时间窗共同决定", UiColors.TEXT_SECONDARY)));
        return panel;
    }

    private JPanel pair(String leftLabel, java.awt.Component left, String rightLabel, java.awt.Component right) {
        JPanel pair = new JPanel(new java.awt.GridLayout(1, 2, UiSpacing.LG, 0));
        pair.setOpaque(false);
        pair.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        pair.add(row(leftLabel, left));
        pair.add(row(rightLabel, right));
        return pair;
    }

    private JPanel row(String text, java.awt.Component input) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        JLabel caption = label(text, UiColors.TEXT_PRIMARY);
        caption.setAlignmentX(LEFT_ALIGNMENT);
        row.add(caption);
        row.add(Box.createVerticalStrut(UiSpacing.SM));
        if (input instanceof javax.swing.JComponent component) component.setAlignmentX(LEFT_ALIGNMENT);
        row.add(input);
        row.add(Box.createVerticalStrut(UiSpacing.MD));
        return row;
    }

    private JPanel actions() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_DEFAULT));
        panel.add(error);
        panel.add(Box.createHorizontalGlue());
        JButton cancel = AbstractCoursePanel.secondary("取消");
        cancel.addActionListener(event -> dispose());
        panel.add(cancel);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(save);
        return panel;
    }

    private void submit() {
        error.setText(" ");
        CompletableFuture<TermView> request;
        try {
            String cleanCode = required(code, "请输入学期代码");
            String cleanName = required(name, "请输入学期名称");
            LocalDate cleanStart = LocalDate.parse(required(startDate, "请输入开学日期"));
            LocalDate cleanEnd = LocalDate.parse(required(endDate, "请输入结束日期"));
            Instant cleanEnrollmentStart = instant(enrollmentStart, "请输入选课开始时间");
            Instant cleanEnrollmentEnd = instant(enrollmentEnd, "请输入选课结束时间");
            Instant cleanAdjustmentStart = instant(adjustmentStart, "请输入退改补开始时间");
            Instant cleanAdjustmentEnd = instant(adjustmentEnd, "请输入退改补结束时间");
            String cleanStatus = (String) status.getSelectedItem();
            if (existing == null) {
                request = gateway.createTerm(new CreateTermCommand(cleanCode, cleanName, cleanStart, cleanEnd,
                        cleanEnrollmentStart, cleanEnrollmentEnd, cleanAdjustmentStart, cleanAdjustmentEnd, cleanStatus));
            } else {
                request = gateway.updateTerm(new UpdateTermCommand(existing.termId(), cleanCode, cleanName,
                        cleanStart, cleanEnd, cleanEnrollmentStart, cleanEnrollmentEnd, cleanAdjustmentStart,
                        cleanAdjustmentEnd, cleanStatus, existing.rowVersion()));
            }
        } catch (DateTimeParseException invalid) {
            error.setText("日期或时间格式不正确，请按提示格式填写");
            return;
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage() == null || "invalid term".equals(invalid.getMessage())
                    ? "请检查日期顺序和选课时间窗" : invalid.getMessage());
            return;
        }
        String idle = save.getText();
        save.setEnabled(false);
        save.setText(existing == null ? "正在创建…" : "正在保存…");
        request.whenComplete((saved, failure) -> SwingUtilities.invokeLater(() -> {
            save.setEnabled(true);
            save.setText(idle);
            if (failure != null) { error.setText("保存失败，记录可能已被修改，请刷新后重试"); return; }
            onSaved.run();
            dispose();
        }));
    }

    private void fill(TermView value) {
        code.setText(value.termCode());
        name.setText(value.termName());
        startDate.setText(value.startDate().toString());
        endDate.setText(value.endDate().toString());
        enrollmentStart.setText(format(value.enrollmentStartAt()));
        enrollmentEnd.setText(format(value.enrollmentEndAt()));
        adjustmentStart.setText(format(value.adjustmentStartAt()));
        adjustmentEnd.setText(format(value.adjustmentEndAt()));
        status.setSelectedItem(value.termStatus());
    }

    private static Instant instant(JTextField field, String message) {
        return LocalDateTime.parse(required(field, message), DATE_TIME).atZone(CAMPUS_ZONE).toInstant();
    }

    private static String format(Instant value) { return DATE_TIME.format(value.atZone(CAMPUS_ZONE)); }

    private static JTextField field(String name) {
        JTextField field = new JTextField();
        field.setFont(UiTypography.BODY);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        field.setPreferredSize(new Dimension(260, UiDimensions.CONTROL_HEIGHT));
        field.getAccessibleContext().setAccessibleName(name);
        return field;
    }

    private static String required(JTextField field, String message) {
        String value = field.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(message);
        return value;
    }

    private static JLabel label(String text, java.awt.Color color) {
        JLabel label = new JLabel(text);
        label.setFont(UiTypography.BODY);
        label.setForeground(color);
        return label;
    }
}
