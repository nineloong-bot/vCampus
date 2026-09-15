package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.*;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/** Page-embedded form for creating or updating one catalog course. */
public final class CourseEditorPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(0, UiSpacing.MD));
    private final UiAsyncGuard guard = new UiAsyncGuard();
    private final CourseUiGateway gateway;
    private final CourseView existing;
    private final Runnable saved;
    private final Runnable cancelled;
    private final CurriculumCourseEditorFields curriculum;
    private final JTextArea description = new JTextArea(3, 22);
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
        curriculum = new CurriculumCourseEditorFields(gateway);
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG));
        save = AbstractCoursePanel.primary(existing == null ? "创建课程" : "保存修改");
        save.addActionListener(event -> submit());
        root.add(CourseEditorCard.create(form(), actions()), BorderLayout.CENTER);
        if (existing != null) fill(existing);
        initial = snapshot();
        root.setMinimumSize(new Dimension(430, 420));
        root.setPreferredSize(new Dimension(500, 560));
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return !snapshot().equals(initial); }
    @Override public void onOpened() { guard.activate(); }
    @Override public void onClosed() { guard.deactivate(); }

    private JPanel form() {
        JPanel panel = vertical();
        panel.add(curriculum.component());
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
            if (existing == null) {
                CurriculumCourseCandidate source = curriculum.requireSelection();
                request = gateway.createCourse(new CreateCourseCommand(source.courseCode(), source.courseName(),
                        source.credits(), source.totalHours(), value.description, value.active,
                        source.planCourseId()));
            } else {
                request = gateway.updateCourse(new UpdateCourseCommand(existing.courseId(), existing.courseCode(),
                        existing.courseName(), existing.credit(), existing.totalHours(), value.description,
                        value.active, existing.rowVersion()));
            }
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
        curriculum.showExisting(value.courseCode(), value.courseName(), value.credit().toPlainString(),
                value.totalHours(), value.departmentName());
        description.setText(value.description()); active.setSelected(value.active());
    }

    private Snapshot snapshot() {
        return new Snapshot(description.getText().trim(), active.isSelected());
    }

    private JPanel row(String text, Component input) {
        JPanel row = vertical();
        row.add(AbstractCoursePanel.label(text, UiTypography.BODY, UiColors.TEXT_PRIMARY));
        row.add(Box.createVerticalStrut(UiSpacing.XS)); row.add(input); row.add(Box.createVerticalStrut(UiSpacing.SM));
        return row;
    }

    private static JPanel vertical() { JPanel panel = new JPanel(); panel.setOpaque(false); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); return panel; }
    private static JLabel label(String text, Color color) { return AbstractCoursePanel.label(text, UiTypography.BODY, color); }

    private record Snapshot(String description, boolean active) { Snapshot validated() { return this; } }
}
