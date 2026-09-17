package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.ScheduleItem;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.BorderFactory;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import edu.seu.vcampus.client.core.ui.autocomplete.SuggestionLoader;

/** Structured editor for one or more offering schedule rows. */
public final class OfferingScheduleEditorPanel extends JPanel {
    private final JPanel rowsPanel = new JPanel();
    private final List<OfferingScheduleRowPanel> rows = new ArrayList<>();
    private final SuggestionLoader classroomLoader;
    private final String defaultClassroom;

    public OfferingScheduleEditorPanel() {
        this((query, limit) -> java.util.concurrent.CompletableFuture.completedFuture(
                List.of(new edu.seu.vcampus.client.core.ui.autocomplete.AutocompleteChoice(
                        "待定", "待定", ""))), "待定");
    }

    /** Creates a schedule editor backed by the classroom suggestion endpoint. */
    public OfferingScheduleEditorPanel(SuggestionLoader classroomLoader) {
        this(classroomLoader, null);
    }

    private OfferingScheduleEditorPanel(SuggestionLoader classroomLoader, String defaultClassroom) {
        super(new BorderLayout(0, UiSpacing.SM));
        this.classroomLoader = java.util.Objects.requireNonNull(classroomLoader, "classroomLoader");
        this.defaultClassroom = defaultClassroom;
        setOpaque(false);
        setName("offering-schedule-card");
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(UiSpacing.SM, UiSpacing.SM, UiSpacing.SM, UiSpacing.SM)));
        rowsPanel.setOpaque(false);
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        add(rowsPanel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        JButton add = AbstractCoursePanel.secondary("添加上课时间");
        add.addActionListener(event -> addDefaultRow());
        actions.add(add);
        add(actions, BorderLayout.SOUTH);
    }

    /** Replaces all rows with the supplied aggregate schedule values. */
    public void setSchedules(List<ScheduleItem> schedules) {
        rows.clear();
        rowsPanel.removeAll();
        for (ScheduleItem item : schedules) addRow(new OfferingScheduleRowPanel(
                item, this::removeRow, classroomLoader));
        revalidate();
        repaint();
    }

    /** Adds a localized row with the standard Monday, periods 1-2, weeks 1-16 default. */
    public void addDefaultRow() {
        OfferingScheduleRowPanel row = new OfferingScheduleRowPanel(null, this::removeRow, classroomLoader);
        if (defaultClassroom != null) row.setClassroom(defaultClassroom);
        addRow(row);
        revalidate();
        repaint();
    }

    /** Maps controls directly to typed protocol values without parsing a CSV intermediary. */
    public List<CreateOfferingCommand.ScheduleInput> scheduleInputs() {
        if (rows.isEmpty()) throw new IllegalArgumentException("请至少添加一行上课时间");
        List<CreateOfferingCommand.ScheduleInput> inputs = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) inputs.add(rows.get(index).toInput(index + 1));
        return List.copyOf(inputs);
    }

    /** Returns a stable snapshot of every editable schedule value, including an empty row set. */
    public String fingerprint() {
        return rows.stream().map(OfferingScheduleRowPanel::fingerprint)
                .collect(java.util.stream.Collectors.joining("\u0001"));
    }

    private void addRow(OfferingScheduleRowPanel row) {
        rows.add(row);
        renderRows();
    }

    private void removeRow(OfferingScheduleRowPanel row) {
        if (!rows.remove(row)) return;
        renderRows();
        revalidate();
        repaint();
    }

    private void renderRows() {
        rowsPanel.removeAll();
        for (OfferingScheduleRowPanel row : rows) {
            rowsPanel.add(row);
            rowsPanel.add(Box.createVerticalStrut(UiSpacing.SM));
        }
        renameRows();
    }

    private void renameRows() {
        for (int index = 0; index < rows.size(); index++) rows.get(index).setRowNumber(index + 1);
    }

}
