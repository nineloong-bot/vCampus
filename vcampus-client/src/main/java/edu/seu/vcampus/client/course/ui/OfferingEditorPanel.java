package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.autocomplete.AutocompleteSelectionField;
import edu.seu.vcampus.client.core.ui.editor.*;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Wide page-embedded editor for a teaching class and its schedule rows. */
public final class OfferingEditorPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(0, UiSpacing.MD));
    private final UiAsyncGuard guard = new UiAsyncGuard();
    private final CourseUiGateway gateway;
    private final OfferingSummary existing;
    private final Runnable saved;
    private final Runnable cancelled;
    private final AutocompleteSelectionField course;
    private final AutocompleteSelectionField teacher;
    private final JTextField className = new JTextField();
    private final JSpinner capacity;
    private final JSpinner retakeCapacity;
    private final JComboBox<Status> status = new JComboBox<>(Status.values());
    private final OfferingScheduleEditorPanel schedules = new OfferingScheduleEditorPanel();
    private final JLabel error = AbstractCoursePanel.label(" ", UiTypography.BODY, UiColors.ACCENT);
    private final JButton save;
    private Snapshot initial;
    private boolean active;

    /** Creates an offering editor; reference suggestions are loaded through the existing gateway. */
    public OfferingEditorPanel(CourseUiGateway gateway, OfferingSummary existing,
                               Runnable saved, Runnable cancelled) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.existing = existing;
        this.saved = Objects.requireNonNull(saved, "saved");
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
        OfferingReferenceLoader loader = new OfferingReferenceLoader(gateway);
        course = new AutocompleteSelectionField(loader::searchCourses);
        teacher = new AutocompleteSelectionField(loader::searchTeachers);
        course.inputComponent().getAccessibleContext().setAccessibleName("课程");
        teacher.inputComponent().getAccessibleContext().setAccessibleName("教师");
        className.getAccessibleContext().setAccessibleName("教学班名称");
        int normalMinimum = existing == null ? 1 : Math.max(1, existing.enrolledCount());
        int normalValue = existing == null ? 40 : Math.max(normalMinimum, existing.capacity());
        int retakeMinimum = existing == null ? 0 : existing.retakeEnrolledCount();
        int retakeValue = existing == null ? 5 : Math.max(retakeMinimum, existing.retakeCapacity());
        capacity = spinner(normalValue, normalMinimum, "容量");
        retakeCapacity = spinner(retakeValue, retakeMinimum, "重修容量");
        status.getAccessibleContext().setAccessibleName("教学班状态");
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG));
        save = AbstractCoursePanel.primary(existing == null ? "创建教学班" : "保存修改");
        save.addActionListener(event -> submit());
        root.add(CourseEditorCard.create(form(), actions()), BorderLayout.CENTER);
        if (existing == null) schedules.addDefaultRow(); else fill(existing);
        initial = existing == null ? snapshot() : new Snapshot(existing.courseId(),
                existing.teacherUserId(), existing.className(), existing.capacity(),
                existing.retakeCapacity(), Status.valueOf(existing.offeringStatus()), schedules.fingerprint());
        root.setMinimumSize(new Dimension(760, 520));
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public EditorPlacement preferredPlacement() { return EditorPlacement.RIGHT; }
    @Override public boolean isDirty() { return !snapshot().equals(initial); }
    @Override public void onOpened() { active = true; guard.activate(); resolveExistingTeacher(); }
    @Override public void onClosed() { active = false; guard.deactivate(); }

    private JScrollPane form() {
        JPanel panel = new JPanel(); panel.setOpaque(false); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(pair("课程（必填）", course, "教师（必填）", teacher));
        panel.add(pair("教学班名称（必填）", className, "教学班状态", status));
        panel.add(pair("普通选课容量（必填）", capacity, "重修专用容量", retakeCapacity));
        panel.add(AbstractCoursePanel.label("上课安排（必填）", UiTypography.BODY, UiColors.TEXT_PRIMARY));
        panel.add(schedules);
        JScrollPane scroll = new JScrollPane(panel); scroll.setBorder(null); scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel pair(String leftText, Component left, String rightText, Component right) {
        JPanel pair = new JPanel(new GridLayout(1, 2, UiSpacing.LG, 0)); pair.setOpaque(false);
        pair.add(row(leftText, left)); pair.add(row(rightText, right)); return pair;
    }

    private JPanel row(String text, Component input) {
        JPanel row = new JPanel(); row.setOpaque(false); row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        if (!text.isBlank()) {
            JLabel label = AbstractCoursePanel.label(text, UiTypography.BODY, UiColors.TEXT_PRIMARY);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(label);
        }
        if (input instanceof JComponent component) component.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(Box.createVerticalStrut(UiSpacing.XS)); row.add(input); row.add(Box.createVerticalStrut(UiSpacing.SM)); return row;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(); panel.setOpaque(false); panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(error); panel.add(Box.createHorizontalGlue());
        JButton cancel = AbstractCoursePanel.secondary("取消"); cancel.addActionListener(event -> cancelled.run());
        panel.add(cancel); panel.add(Box.createHorizontalStrut(UiSpacing.SM)); panel.add(save); return panel;
    }

    private void resolveExistingTeacher() {
        if (existing == null) return;
        course.setSelection(existing.courseId(), existing.courseCode() + " · " + existing.courseName());
        teacher.setSelection(existing.teacherUserId(), existing.teacherUserId());
        gateway.resolveTeacher(existing.teacherUserId()).whenComplete((value, failure) -> SwingUtilities.invokeLater(() -> {
            if (active && failure == null && value.isPresent()) {
                teacher.setSelection(value.get().userId(), value.get().loginId());
            }
        }));
    }

    private void submit() {
        error.setText(" ");
        java.util.concurrent.CompletableFuture<OfferingView> operation;
        try {
            String selectedCourse = course.requireSelection().id();
            String selectedTeacher = teacher.requireSelection().id();
            String cleanName = className.getText().strip();
            if (cleanName.isEmpty()) throw new IllegalArgumentException("请输入教学班名称");
            int normal = ((Number) capacity.getValue()).intValue();
            int retake = ((Number) retakeCapacity.getValue()).intValue();
            List<CreateOfferingCommand.ScheduleInput> rows = schedules.scheduleInputs();
            Status selectedStatus = (Status) status.getSelectedItem();
            operation = existing == null
                    ? gateway.createOffering(new CreateOfferingCommand(selectedCourse, selectedTeacher,
                    cleanName, normal, retake, selectedStatus.name(), rows))
                    : gateway.updateOffering(new UpdateOfferingCommand(existing.offeringId(),
                    selectedCourse, selectedTeacher, cleanName, normal, retake, selectedStatus.name(),
                    existing.rowVersion(), rows));
        } catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); return; }
        save.setEnabled(false); long request = guard.begin();
        operation.whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
            if (!guard.accepts(request)) return; save.setEnabled(true);
            if (failure != null) { error.setText("保存失败，请刷新后重试"); return; }
            initial = snapshot(); saved.run();
        }));
    }

    private void fill(OfferingSummary value) { className.setText(value.className()); capacity.setValue(value.capacity());
        retakeCapacity.setValue(value.retakeCapacity()); status.setSelectedItem(Status.valueOf(value.offeringStatus())); schedules.setSchedules(value.schedules()); }
    private Snapshot snapshot() {
        return new Snapshot(course.selectedId().orElse(null), teacher.selectedId().orElse(null),
                className.getText(), ((Number) capacity.getValue()).intValue(),
                ((Number) retakeCapacity.getValue()).intValue(), status.getSelectedItem(),
                schedules.fingerprint());
    }
    private static JSpinner spinner(int value, int minimum, String name) { JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, minimum, 10_000, 1)); spinner.getAccessibleContext().setAccessibleName(name); return spinner; }
    private record Snapshot(String courseId, String teacherId, String className,
                            int capacity, int retakeCapacity, Object status, String schedules) { }
    private enum Status { DRAFT, OPEN, CLOSED, CANCELLED }
}
