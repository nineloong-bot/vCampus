package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.*;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.Objects;

/** Page-embedded form for creating or updating one catalog course. */
public final class CourseEditorPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(0, UiSpacing.MD));
    private final UiAsyncGuard guard = new UiAsyncGuard();
    private final CourseUiGateway gateway;
    private final CourseView existing;
    private final Runnable saved;
    private final Runnable cancelled;
    private final JTextField code = field("课程代码");
    private final JTextField name = field("课程名称");
    private final JSpinner credit = spinner(new BigDecimalModel(), "学分");
    private final JSpinner hours = spinner(new SpinnerNumberModel(32, 1, 1000, 1), "总学时");
    private final JTextArea description = new JTextArea(4, 28);
    private final JCheckBox active = new JCheckBox("启用课程", true);
    private final JLabel error = label(" ", UiColors.ACCENT);
    private final JButton save;
    private Snapshot initial;

    /** Creates an embedded editor and reports successful saves or cancellation. */
    public CourseEditorPanel(CourseUiGateway gateway, CourseView existing, Runnable saved, Runnable cancelled) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.existing = existing;
        this.saved = Objects.requireNonNull(saved, "saved");
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG));
        root.add(form(), BorderLayout.CENTER);
        save = AbstractCoursePanel.primary(existing == null ? "创建课程" : "保存修改");
        save.addActionListener(event -> submit());
        root.add(actions(), BorderLayout.SOUTH);
        if (existing != null) fill(existing);
        initial = snapshot();
        root.setMinimumSize(new Dimension(400, 420));
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return !snapshot().equals(initial); }
    @Override public void onOpened() { guard.activate(); }
    @Override public void onClosed() { guard.deactivate(); }

    private JPanel form() {
        JPanel panel = vertical();
        panel.add(row("课程代码（必填）", code));
        panel.add(row("课程名称（必填）", name));
        panel.add(row("学分（必填）", credit));
        panel.add(row("总学时（必填）", hours));
        panel.add(AbstractCoursePanel.label("课程简介", UiTypography.BODY, UiColors.TEXT_PRIMARY));
        description.setFont(UiTypography.BODY);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.getAccessibleContext().setAccessibleName("课程简介");
        panel.add(new JScrollPane(description));
        active.setOpaque(false);
        active.setFont(UiTypography.BODY);
        panel.add(active);
        return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(error);
        panel.add(Box.createHorizontalGlue());
        JButton cancel = AbstractCoursePanel.secondary("取消");
        cancel.addActionListener(event -> cancelled.run());
        panel.add(cancel);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(save);
        return panel;
    }

    private void submit() {
        error.setText(" ");
        java.util.concurrent.CompletableFuture<CourseView> request;
        try {
            Snapshot value = snapshot().validated();
            request = existing == null
                    ? gateway.createCourse(new CreateCourseCommand(value.code, value.name, value.credit,
                    value.hours, value.description, value.active))
                    : gateway.updateCourse(new UpdateCourseCommand(existing.courseId(), value.code, value.name,
                    value.credit, value.hours, value.description, value.active, existing.rowVersion()));
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage());
            return;
        }
        save.setEnabled(false);
        long generation = guard.begin();
        request.whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
            if (!guard.accepts(generation)) return;
            save.setEnabled(true);
            if (failure != null) { error.setText("保存失败，记录可能已被修改，请刷新后重试"); return; }
            initial = snapshot();
            saved.run();
        }));
    }

    private void fill(CourseView value) {
        code.setText(value.courseCode()); name.setText(value.courseName()); credit.setValue(value.credit());
        hours.setValue(value.totalHours()); description.setText(value.description()); active.setSelected(value.active());
    }

    private Snapshot snapshot() {
        return new Snapshot(code.getText().trim(), name.getText().trim(), decimal((Number) credit.getValue()),
                ((Number) hours.getValue()).intValue(), description.getText().trim(), active.isSelected());
    }

    private JPanel row(String text, Component input) {
        JPanel row = vertical();
        row.add(AbstractCoursePanel.label(text, UiTypography.BODY, UiColors.TEXT_PRIMARY));
        row.add(Box.createVerticalStrut(UiSpacing.XS)); row.add(input); row.add(Box.createVerticalStrut(UiSpacing.SM));
        return row;
    }

    private static JPanel vertical() { JPanel panel = new JPanel(); panel.setOpaque(false); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); return panel; }
    private static JTextField field(String name) { JTextField field = new JTextField(); field.getAccessibleContext().setAccessibleName(name); field.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT)); return field; }
    private static JSpinner spinner(SpinnerNumberModel model, String name) { JSpinner spinner = new JSpinner(model); spinner.getAccessibleContext().setAccessibleName(name); spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT)); return spinner; }
    private static JLabel label(String text, Color color) { return AbstractCoursePanel.label(text, UiTypography.BODY, color); }
    private static BigDecimal decimal(Number value) { return value instanceof BigDecimal number ? number : new BigDecimal(value.toString()); }

    private record Snapshot(String code, String name, BigDecimal credit, int hours, String description, boolean active) {
        Snapshot validated() { if (code.isBlank()) throw new IllegalArgumentException("请输入课程代码"); if (name.isBlank()) throw new IllegalArgumentException("请输入课程名称"); return this; }
    }

    private static final class BigDecimalModel extends SpinnerNumberModel {
        BigDecimalModel() { super(new BigDecimal("1.0"), new BigDecimal("0.5"), new BigDecimal("20.0"), new BigDecimal("0.5")); }
        @Override public Object getNextValue() { return step(new BigDecimal("0.5")); }
        @Override public Object getPreviousValue() { return step(new BigDecimal("-0.5")); }
        private Object step(BigDecimal amount) { BigDecimal value = decimal(getNumber()).add(amount); return value.compareTo((BigDecimal)getMinimum()) < 0 || value.compareTo((BigDecimal)getMaximum()) > 0 ? null : value; }
    }
}
