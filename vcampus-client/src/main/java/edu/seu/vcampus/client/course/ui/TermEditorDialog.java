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
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;

/** Modal create/edit form for a complete server-authoritative academic term. */
final class TermEditorDialog extends JDialog {
    private final UiAsyncGuard asyncGuard = new UiAsyncGuard();
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private final CourseUiGateway gateway;
    private final TermView existing;
    private final Runnable onSaved;
    private final JTextField code = field("学期代码");
    private final JTextField name = field("学期名称");
    private final JSpinner startDate;
    private final JSpinner endDate;
    private final JSpinner enrollmentStart;
    private final JSpinner enrollmentEnd;
    private final JSpinner adjustmentStart;
    private final JSpinner adjustmentEnd;
    private final JComboBox<StatusChoice> status = new JComboBox<>(new StatusChoice[]{
            new StatusChoice("PLANNED", "计划中"),
            new StatusChoice("ACTIVE", "进行中"),
            new StatusChoice("CLOSED", "已关闭")
    });
    private final JLabel error = label(" ", UiColors.ACCENT);
    private final JButton save;

    TermEditorDialog(Window owner, CourseUiGateway gateway, TermView existing, Runnable onSaved) {
        super(owner, existing == null ? "新建学期" : "编辑学期", ModalityType.APPLICATION_MODAL);
        this.gateway = gateway;
        this.existing = existing;
        this.onSaved = onSaved;
        LocalDate defaultStart = LocalDate.now(CAMPUS_ZONE);
        startDate = dateSpinner(atStartOfDay(defaultStart), Calendar.DAY_OF_MONTH, "yyyy-MM-dd", "开学日期");
        endDate = dateSpinner(atStartOfDay(defaultStart.plusMonths(4)), Calendar.DAY_OF_MONTH,
                "yyyy-MM-dd", "结束日期");
        enrollmentStart = dateSpinner(at(defaultStart.minusDays(14), 8, 0), Calendar.MINUTE,
                "yyyy-MM-dd HH:mm", "选课开始");
        enrollmentEnd = dateSpinner(at(defaultStart.minusDays(1), 23, 59), Calendar.MINUTE,
                "yyyy-MM-dd HH:mm", "选课结束");
        adjustmentStart = dateSpinner(at(defaultStart, 8, 0), Calendar.MINUTE,
                "yyyy-MM-dd HH:mm", "退改补开始");
        adjustmentEnd = dateSpinner(at(defaultStart.plusDays(7), 23, 59), Calendar.MINUTE,
                "yyyy-MM-dd HH:mm", "退改补结束");
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
            LocalDate cleanStart = date(startDate);
            LocalDate cleanEnd = date(endDate);
            Instant cleanEnrollmentStart = instant(enrollmentStart);
            Instant cleanEnrollmentEnd = instant(enrollmentEnd);
            Instant cleanAdjustmentStart = instant(adjustmentStart);
            Instant cleanAdjustmentEnd = instant(adjustmentEnd);
            CourseFormValidation.requireOrdered(cleanStart, cleanEnd, "结束日期必须晚于开学日期");
            CourseFormValidation.requireOrdered(cleanEnrollmentStart, cleanEnrollmentEnd,
                    "选课结束必须晚于选课开始");
            CourseFormValidation.requireOrdered(cleanAdjustmentStart, cleanAdjustmentEnd,
                    "退改补结束必须晚于退改补开始");
            String cleanStatus = ((StatusChoice) status.getSelectedItem()).code();
            if (existing == null) {
                request = gateway.createTerm(new CreateTermCommand(cleanCode, cleanName, cleanStart, cleanEnd,
                        cleanEnrollmentStart, cleanEnrollmentEnd, cleanAdjustmentStart, cleanAdjustmentEnd, cleanStatus));
            } else {
                request = gateway.updateTerm(new UpdateTermCommand(existing.termId(), cleanCode, cleanName,
                        cleanStart, cleanEnd, cleanEnrollmentStart, cleanEnrollmentEnd, cleanAdjustmentStart,
                        cleanAdjustmentEnd, cleanStatus, existing.rowVersion()));
            }
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage() == null || "invalid term".equals(invalid.getMessage())
                    ? "请检查日期顺序和选课时间窗" : invalid.getMessage());
            return;
        }
        String idle = save.getText();
        save.setEnabled(false);
        save.setText(existing == null ? "正在创建…" : "正在保存…");
        long asyncRequest = asyncGuard.begin();
        request.whenComplete((saved, failure) -> SwingUtilities.invokeLater(() -> {
            if (!asyncGuard.accepts(asyncRequest)) return;
            save.setEnabled(true);
            save.setText(idle);
            if (failure != null) { error.setText("保存失败，记录可能已被修改，请刷新后重试"); return; }
            onSaved.run();
            dispose();
        }));
    }

    @Override public void dispose() {
        asyncGuard.deactivate();
        super.dispose();
    }

    private void fill(TermView value) {
        code.setText(value.termCode());
        name.setText(value.termName());
        startDate.setValue(atStartOfDay(value.startDate()));
        endDate.setValue(atStartOfDay(value.endDate()));
        enrollmentStart.setValue(Date.from(value.enrollmentStartAt()));
        enrollmentEnd.setValue(Date.from(value.enrollmentEndAt()));
        adjustmentStart.setValue(Date.from(value.adjustmentStartAt()));
        adjustmentEnd.setValue(Date.from(value.adjustmentEndAt()));
        status.setSelectedItem(statusChoice(value.termStatus()));
    }

    private static JSpinner dateSpinner(Date value, int calendarField, String pattern, String accessibleName) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(value, null, null, calendarField));
        spinner.setFont(UiTypography.BODY);
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        spinner.setPreferredSize(new Dimension(260, UiDimensions.CONTROL_HEIGHT));
        spinner.getAccessibleContext().setAccessibleName(accessibleName);
        dateEditor(spinner, pattern);
        return spinner;
    }

    private static JSpinner.DateEditor dateEditor(JSpinner spinner, String pattern) {
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, pattern);
        editor.getFormat().setTimeZone(TimeZone.getTimeZone(CAMPUS_ZONE));
        spinner.setEditor(editor);
        return editor;
    }

    private static Instant instant(JSpinner spinner) {
        return ((Date) spinner.getValue()).toInstant();
    }

    private static LocalDate date(JSpinner spinner) {
        return ((Date) spinner.getValue()).toInstant().atZone(CAMPUS_ZONE).toLocalDate();
    }

    private static Date atStartOfDay(LocalDate value) {
        return Date.from(value.atStartOfDay(CAMPUS_ZONE).toInstant());
    }

    private static Date at(LocalDate value, int hour, int minute) {
        return Date.from(value.atTime(hour, minute).atZone(CAMPUS_ZONE).toInstant());
    }

    private static StatusChoice statusChoice(String code) {
        return switch (code) {
            case "PLANNED" -> new StatusChoice("PLANNED", "计划中");
            case "ACTIVE" -> new StatusChoice("ACTIVE", "进行中");
            case "CLOSED" -> new StatusChoice("CLOSED", "已关闭");
            default -> throw new IllegalArgumentException("invalid term");
        };
    }

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

    private record StatusChoice(String code, String label) {
        @Override public String toString() { return label; }
    }
}
