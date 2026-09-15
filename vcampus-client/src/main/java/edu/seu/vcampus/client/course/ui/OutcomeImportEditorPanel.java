package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.*;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Wide embedded editor for pass/fail course-outcome imports. */
public final class OutcomeImportEditorPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(0, UiSpacing.MD));
    private final CourseUiGateway gateway;
    private final Runnable saved;
    private final Runnable cancelled;
    private final JTextArea input = new JTextArea(10, 72);
    private final JLabel status = AbstractCoursePanel.label(" ", UiTypography.BODY, UiColors.ACCENT);
    private final JButton submit = AbstractCoursePanel.primary("执行导入");

    /** Creates an import editor that reports successful completion and cancellation. */
    public OutcomeImportEditorPanel(CourseUiGateway gateway, Runnable saved, Runnable cancelled) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.saved = Objects.requireNonNull(saved, "saved");
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG));
        root.add(AbstractCoursePanel.label("每行：学生编号,课程编号,学期编号,PASSED或FAILED,来源唯一标识",
                UiTypography.BODY, UiColors.TEXT_PRIMARY), BorderLayout.NORTH);
        input.getAccessibleContext().setAccessibleName("课程结果批量导入内容");
        root.add(new JScrollPane(input), BorderLayout.CENTER);
        submit.addActionListener(event -> submit());
        root.add(actions(), BorderLayout.SOUTH);
        root.setMinimumSize(new Dimension(720, 300));
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return !input.getText().isBlank(); }

    private JPanel actions() {
        JPanel panel = new JPanel(); panel.setOpaque(false); panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(status); panel.add(Box.createHorizontalGlue());
        JButton cancel = AbstractCoursePanel.secondary("取消"); cancel.addActionListener(event -> cancelled.run());
        panel.add(cancel); panel.add(Box.createHorizontalStrut(UiSpacing.SM)); panel.add(submit); return panel;
    }

    private void submit() {
        List<ImportCourseOutcomesCommand.OutcomeEntry> entries;
        try { entries = parse(input.getText()); }
        catch (IllegalArgumentException invalid) { status.setText(invalid.getMessage()); return; }
        submit.setEnabled(false); status.setText("正在导入…");
        gateway.importOutcomes(new ImportCourseOutcomesCommand(entries)).whenComplete((ignored, failure) ->
                SwingUtilities.invokeLater(() -> {
                    submit.setEnabled(true);
                    if (failure != null) { status.setText("导入失败，请检查内容或连接后重试"); return; }
                    status.setText("已导入 " + entries.size() + " 条课程结果"); input.setText(""); saved.run();
                }));
    }

    static List<ImportCourseOutcomesCommand.OutcomeEntry> parse(String text) {
        List<ImportCourseOutcomesCommand.OutcomeEntry> entries = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim(); if (line.isEmpty()) continue;
            String[] fields = line.split(",", -1);
            if (fields.length != 5) throw new IllegalArgumentException("第 " + (index + 1) + " 行应包含 5 个字段");
            for (int field = 0; field < fields.length; field++) fields[field] = fields[field].trim();
            CourseOutcome outcome = switch (fields[3].toUpperCase(Locale.ROOT)) {
                case "PASSED", "通过" -> CourseOutcome.PASSED;
                case "FAILED", "未通过" -> CourseOutcome.FAILED;
                default -> throw new IllegalArgumentException("第 " + (index + 1) + " 行结果只能是 PASSED/FAILED");
            };
            try { entries.add(new ImportCourseOutcomesCommand.OutcomeEntry(fields[0], fields[1], fields[2], outcome, fields[4])); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("第 " + (index + 1) + " 行存在空字段或来源标识过长"); }
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("请至少输入一条课程结果后再导入");
        return List.copyOf(entries);
    }
}
