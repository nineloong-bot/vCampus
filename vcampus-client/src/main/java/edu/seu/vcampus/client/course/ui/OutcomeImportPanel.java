package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.CourseOutcome;
import edu.seu.vcampus.common.course.ImportCourseOutcomesCommand;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Administrator pass/fail outcome import; deliberately has no numeric grade field. */
public final class OutcomeImportPanel extends AbstractCoursePanel {
    private final CourseUiGateway gateway;
    private final JTextArea input = new JTextArea(12, 72);
    private final JLabel result = label("尚未提交导入", UiTypography.BODY, UiColors.TEXT_SECONDARY);
    private final JButton submit = primary("导入课程结果");

    public OutcomeImportPanel(CourseUiGateway gateway) {
        super("课程结果导入", "批量导入通过或未通过结果，用于判断重修资格；系统不接收具体分值。");
        this.gateway = gateway;
        JPanel content = new JPanel(new BorderLayout(0, UiSpacing.MD));
        content.setOpaque(false);
        content.add(label("每行格式：学生编号,课程编号,学期编号,PASSED或FAILED,来源唯一标识",
                UiTypography.BODY, UiColors.TEXT_PRIMARY), BorderLayout.NORTH);
        input.setFont(UiTypography.BODY);
        input.setLineWrap(false);
        input.setBorder(BorderFactory.createEmptyBorder(UiSpacing.SM, UiSpacing.SM, UiSpacing.SM, UiSpacing.SM));
        input.getAccessibleContext().setAccessibleName("课程结果批量导入内容");
        JScrollPane scroll = new JScrollPane(input);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        content.add(scroll, BorderLayout.CENTER);
        content.add(actions(), BorderLayout.SOUTH);
        body.add(content, BorderLayout.CENTER);
    }

    private JPanel actions() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_DEFAULT));
        panel.add(result);
        panel.add(Box.createHorizontalGlue());
        JButton clear = secondary("清空内容");
        clear.addActionListener(event -> { input.setText(""); result.setText("尚未提交导入"); showState(ViewState.INITIAL, ""); });
        panel.add(clear);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        submit.addActionListener(event -> submit());
        panel.add(submit);
        return panel;
    }

    private void submit() {
        List<ImportCourseOutcomesCommand.OutcomeEntry> entries;
        try { entries = parse(input.getText()); }
        catch (IllegalArgumentException invalid) {
            showState(ViewState.ERROR, invalid.getMessage());
            return;
        }
        submit.setEnabled(false);
        submit.setText("正在导入…");
        showState(ViewState.SUBMITTING, "正在导入课程结果，请勿重复提交");
        gateway.importOutcomes(new ImportCourseOutcomesCommand(entries)).whenComplete((ignored, error) ->
                SwingUtilities.invokeLater(() -> {
                    submit.setEnabled(true);
                    submit.setText("导入课程结果");
                    if (error != null) { showState(ViewState.ERROR, "导入失败，请检查内容或连接后重试"); return; }
                    result.setText("已导入 " + entries.size() + " 条课程结果；重复来源不会重复写入");
                    showState(ViewState.NORMAL, "");
                }));
    }

    private static List<ImportCourseOutcomesCommand.OutcomeEntry> parse(String text) {
        List<ImportCourseOutcomesCommand.OutcomeEntry> entries = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) continue;
            String[] fields = line.split(",", -1);
            if (fields.length != 5) throw new IllegalArgumentException("第 " + (index + 1) + " 行应包含 5 个逗号分隔字段");
            for (int field = 0; field < fields.length; field++) fields[field] = fields[field].trim();
            CourseOutcome outcome;
            try { outcome = outcome(fields[3]); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("第 " + (index + 1) + " 行结果只能是 PASSED/FAILED（通过/未通过）"); }
            try { entries.add(new ImportCourseOutcomesCommand.OutcomeEntry(fields[0], fields[1], fields[2], outcome, fields[4])); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("第 " + (index + 1) + " 行存在空字段或来源标识过长"); }
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("请至少输入一条课程结果后再导入");
        return List.copyOf(entries);
    }

    private static CourseOutcome outcome(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "PASSED", "通过" -> CourseOutcome.PASSED;
            case "FAILED", "未通过" -> CourseOutcome.FAILED;
            default -> throw new IllegalArgumentException("invalid outcome");
        };
    }
}
