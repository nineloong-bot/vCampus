package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;

/** Embedded editor for creating and maintaining one selection phase. */
public final class SelectionPhaseEditorPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(0, UiSpacing.MD));
    private final CourseUiGateway gateway;
    private final SelectionPhaseView existing;
    private final Runnable saved;
    private final Runnable cancelled;
    private final JComboBox<TermChoice> term = new JComboBox<>();
    private final JComboBox<String> type = new JComboBox<>(new String[]{"正常选课", "退改补选课"});
    private final JComboBox<String> status = new JComboBox<>(new String[]{"草稿", "预选课", "正式开放", "已关闭"});
    private final JTextField title = new JTextField();
    private final JLabel error = AbstractCoursePanel.label(" ", UiTypography.BODY, UiColors.ACCENT);
    private final JButton save;
    private Snapshot initial;

    /** Creates a selection-phase editor from already loaded term references. */
    public SelectionPhaseEditorPanel(CourseUiGateway gateway, List<TermView> terms,
                                     SelectionPhaseView existing, Runnable saved, Runnable cancelled) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.existing = existing;
        this.saved = Objects.requireNonNull(saved, "saved");
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG));
        for (TermView value : terms) term.addItem(new TermChoice(value.termId(), value.termName()));
        term.getAccessibleContext().setAccessibleName("学期");
        type.getAccessibleContext().setAccessibleName("选课阶段类型");
        status.getAccessibleContext().setAccessibleName("阶段状态");
        title.getAccessibleContext().setAccessibleName("学生端标题");
        root.add(form(), BorderLayout.CENTER);
        save = AbstractCoursePanel.primary(existing == null ? "创建阶段" : "保存阶段");
        save.addActionListener(event -> submit());
        root.add(actions(), BorderLayout.SOUTH);
        if (existing != null) fill(existing);
        initial = snapshot();
        root.setMinimumSize(new Dimension(400, 280));
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return !snapshot().equals(initial); }

    private JPanel form() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, UiSpacing.SM)); panel.setOpaque(false);
        panel.add(labeled("学期", term)); panel.add(labeled("阶段", type));
        panel.add(labeled("学生端标题", title)); panel.add(labeled("阶段状态", status)); return panel;
    }

    private JPanel labeled(String text, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(UiSpacing.SM, 0)); panel.setOpaque(false);
        panel.add(AbstractCoursePanel.label(text, UiTypography.BODY, UiColors.TEXT_PRIMARY), BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER); return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(); panel.setOpaque(false); panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(error); panel.add(Box.createHorizontalGlue());
        JButton cancel = AbstractCoursePanel.secondary("取消"); cancel.addActionListener(event -> cancelled.run());
        panel.add(cancel); panel.add(Box.createHorizontalStrut(UiSpacing.SM)); panel.add(save); return panel;
    }

    private void submit() {
        Snapshot value = snapshot();
        if (value.termId == null || value.title.isBlank()) { error.setText("请选择学期并填写学生端标题"); return; }
        save.setEnabled(false);
        java.util.concurrent.CompletableFuture<?> operation;
        if (existing == null) {
            operation = gateway.createSelectionPhase(new CreateSelectionPhaseCommand(
                    value.termId, value.typeIndex == 0 ? "ENROLLMENT" : "ADJUSTMENT", value.title));
        } else if (!statusCode(value.statusIndex).equals(existing.phaseStatus())) {
            operation = gateway.changeSelectionPhaseStatus(new ChangeSelectionPhaseStatusCommand(
                    existing.phaseId(), statusCode(value.statusIndex), existing.rowVersion()));
        } else {
            operation = gateway.updateSelectionPhase(new UpdateSelectionPhaseCommand(
                    existing.phaseId(), value.title, existing.rowVersion()));
        }
        operation.whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
            save.setEnabled(true);
            if (failure != null) { error.setText("保存失败，请刷新后重试"); return; }
            initial = snapshot(); saved.run();
        }));
    }

    private void fill(SelectionPhaseView value) {
        for (int i = 0; i < term.getItemCount(); i++) if (term.getItemAt(i).id.equals(value.termId())) term.setSelectedIndex(i);
        type.setSelectedIndex("ADJUSTMENT".equals(value.phaseType()) ? 1 : 0);
        title.setText(value.displayTitle()); status.setSelectedIndex(statusIndex(value.phaseStatus()));
        term.setEnabled(false); type.setEnabled(false);
    }

    private Snapshot snapshot() { TermChoice choice = (TermChoice) term.getSelectedItem(); return new Snapshot(
            choice == null ? null : choice.id, type.getSelectedIndex(), title.getText().trim(), status.getSelectedIndex()); }
    private static String statusCode(int index) { return switch (index) { case 1 -> "PREVIEW"; case 2 -> "OPEN"; case 3 -> "CLOSED"; default -> "DRAFT"; }; }
    private static int statusIndex(String value) { return switch (value) { case "PREVIEW" -> 1; case "OPEN" -> 2; case "CLOSED" -> 3; default -> 0; }; }
    private record Snapshot(String termId, int typeIndex, String title, int statusIndex) { }
    private record TermChoice(String id, String name) { @Override public String toString() { return name; } }
}
