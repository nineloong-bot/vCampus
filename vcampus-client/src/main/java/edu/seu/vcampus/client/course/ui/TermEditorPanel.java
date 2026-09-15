package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.*;

import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;

/** Page-embedded form for creating or updating an academic term. */
public final class TermEditorPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(0, UiSpacing.MD));
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final UiAsyncGuard guard = new UiAsyncGuard();
    private final CourseUiGateway gateway;
    private final TermView existing;
    private final Runnable saved;
    private final Runnable cancelled;
    private final JTextField code = field("学期代码");
    private final JTextField name = field("学期名称");
    private final JSpinner start = dateSpinner(LocalDate.now(), "开学日期");
    private final JSpinner end = dateSpinner(LocalDate.now().plusMonths(4), "结束日期");
    private final JSpinner year = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 2000, 2200, 1));
    private final JComboBox<AcademicSeason> season = new JComboBox<>(AcademicSeason.values());
    private final JComboBox<Status> status = new JComboBox<>(Status.values());
    private final JLabel error = AbstractCoursePanel.label(" ", UiTypography.BODY, UiColors.ACCENT);
    private final JButton save;
    private Snapshot initial;

    /** Creates an embedded term editor. */
    public TermEditorPanel(CourseUiGateway gateway, TermView existing, Runnable saved, Runnable cancelled) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.existing = existing;
        this.saved = Objects.requireNonNull(saved, "saved");
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG));
        year.getAccessibleContext().setAccessibleName("学年起始年份");
        season.getAccessibleContext().setAccessibleName("培养方案学期");
        status.getAccessibleContext().setAccessibleName("学期状态");
        start.addChangeListener(event -> { if (existing == null) year.setValue(date(start).getYear()); });
        save = AbstractCoursePanel.primary(existing == null ? "创建学期" : "保存修改");
        save.addActionListener(event -> submit());
        root.add(CourseEditorCard.createCompact(form(), actions()), BorderLayout.CENTER);
        if (existing != null) fill(existing); else season.setSelectedItem(AcademicSeason.AUTUMN);
        initial = snapshot();
        root.setMinimumSize(new Dimension(520, 360));
        root.setPreferredSize(new Dimension(540, 520));
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public edu.seu.vcampus.client.core.ui.editor.EditorPlacement preferredPlacement() {
        return edu.seu.vcampus.client.core.ui.editor.EditorPlacement.RIGHT;
    }
    @Override public boolean isDirty() { return !snapshot().equals(initial); }
    @Override public void onOpened() { guard.activate(); }
    @Override public void onClosed() { guard.deactivate(); }

    private JPanel form() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(pair("学期代码（必填）", code, "学期名称（必填）", name));
        panel.add(pair("开学日期", start, "结束日期", end));
        panel.add(pair("学期状态", status, "学年起始年份", year));
        panel.add(pair("培养方案学期", season, "选课开放", new JLabel("在选课阶段中管理")));
        return panel;
    }

    private JPanel pair(String leftText, Component left, String rightText, Component right) {
        JPanel pair = new JPanel(new GridLayout(1, 2, UiSpacing.MD, 0)); pair.setOpaque(false);
        pair.add(row(leftText, left)); pair.add(row(rightText, right)); return pair;
    }

    private JPanel row(String text, Component input) {
        JPanel row = new JPanel(); row.setOpaque(false); row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        JLabel label = AbstractCoursePanel.label(text, UiTypography.BODY, UiColors.TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (input instanceof JComponent component) component.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(label);
        row.add(Box.createVerticalStrut(UiSpacing.XS)); row.add(input); row.add(Box.createVerticalStrut(UiSpacing.SM));
        return row;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(); panel.setOpaque(false); panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(error); panel.add(Box.createHorizontalGlue());
        JButton cancel = AbstractCoursePanel.secondary("取消"); cancel.addActionListener(event -> cancelled.run());
        panel.add(cancel); panel.add(Box.createHorizontalStrut(UiSpacing.SM)); panel.add(save); return panel;
    }

    private void submit() {
        error.setText(" ");
        java.util.concurrent.CompletableFuture<TermView> request;
        try {
            Snapshot value = snapshot().validated();
            Instant enrollmentStart = existing == null ? at(value.start.minusDays(2)) : existing.enrollmentStartAt();
            Instant enrollmentEnd = existing == null ? at(value.start.minusDays(1)) : existing.enrollmentEndAt();
            Instant adjustmentStart = existing == null ? at(value.start) : existing.adjustmentStartAt();
            Instant adjustmentEnd = existing == null ? at(value.start.plusDays(1)) : existing.adjustmentEndAt();
            request = existing == null
                    ? gateway.createTerm(new CreateTermCommand(value.code, value.name, value.start, value.end,
                    value.year, value.season, enrollmentStart, enrollmentEnd, adjustmentStart, adjustmentEnd, value.status.name()))
                    : gateway.updateTerm(new UpdateTermCommand(existing.termId(), value.code, value.name,
                    value.start, value.end, value.year, value.season, enrollmentStart, enrollmentEnd,
                    adjustmentStart, adjustmentEnd, value.status.name(), existing.rowVersion()));
        } catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); return; }
        save.setEnabled(false);
        long generation = guard.begin();
        request.whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
            if (!guard.accepts(generation)) return;
            save.setEnabled(true);
            if (failure != null) { error.setText("保存失败，记录可能已被修改，请刷新后重试"); return; }
            initial = snapshot(); saved.run();
        }));
    }

    private void fill(TermView value) {
        code.setText(value.termCode()); name.setText(value.termName()); start.setValue(toDate(value.startDate()));
        end.setValue(toDate(value.endDate())); year.setValue(value.academicYearStart());
        season.setSelectedItem(value.season()); status.setSelectedItem(Status.of(value.termStatus()));
    }

    private Snapshot snapshot() { return new Snapshot(code.getText().trim(), name.getText().trim(), date(start), date(end),
            ((Number) year.getValue()).intValue(), (AcademicSeason) season.getSelectedItem(), (Status) status.getSelectedItem()); }
    private static JTextField field(String name) { JTextField field = new JTextField(); field.getAccessibleContext().setAccessibleName(name); return field; }
    private static JSpinner dateSpinner(LocalDate value, String name) { JSpinner spinner = new JSpinner(new SpinnerDateModel(toDate(value), null, null, Calendar.DAY_OF_MONTH)); spinner.setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd")); spinner.getAccessibleContext().setAccessibleName(name); return spinner; }
    private static Date toDate(LocalDate value) { return Date.from(value.atStartOfDay(ZONE).toInstant()); }
    private static LocalDate date(JSpinner spinner) { return ((Date) spinner.getValue()).toInstant().atZone(ZONE).toLocalDate(); }
    private static Instant at(LocalDate value) { return value.atStartOfDay(ZONE).toInstant(); }

    private record Snapshot(String code, String name, LocalDate start, LocalDate end, int year, AcademicSeason season, Status status) {
        Snapshot validated() { if (code.isBlank()) throw new IllegalArgumentException("请输入学期代码"); if (name.isBlank()) throw new IllegalArgumentException("请输入学期名称"); CourseFormValidation.requireOrdered(start, end, "结束日期必须晚于开学日期"); return this; }
    }
    private enum Status { PLANNED("计划中"), ACTIVE("进行中"), CLOSED("已关闭"); private final String label; Status(String label){this.label=label;} static Status of(String code){return valueOf(code);} @Override public String toString(){return label;} }
}
