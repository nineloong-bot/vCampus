package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.autocomplete.AutocompleteChoice;
import edu.seu.vcampus.client.core.ui.autocomplete.AutocompleteSelectionField;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.CurriculumCourseCandidate;
import edu.seu.vcampus.common.course.CurriculumCourseCandidateQuery;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Bidirectional curriculum candidate selector with locked authoritative metadata. */
public final class CurriculumCourseEditorFields {
    private final CourseUiGateway gateway;
    private final JPanel root = vertical();
    private final AutocompleteSelectionField code;
    private final AutocompleteSelectionField name;
    private final JTextField credits = locked("学分");
    private final JTextField hours = locked("总学时");
    private final JTextField nature = locked("课程性质");
    private final JTextField department = locked("开课学院");
    private final Map<String, CurriculumCourseCandidate> candidates = new HashMap<>();
    private CurriculumCourseCandidate selection;

    /** Creates a selector backed by the course protocol. */
    public CurriculumCourseEditorFields(CourseUiGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway);
        code = new AutocompleteSelectionField((query, limit) -> search(query, limit, true));
        name = new AutocompleteSelectionField((query, limit) -> search(query, limit, false));
        code.onSelection(this::select);
        name.onSelection(this::select);
        root.add(row("课程代码", code));
        root.add(row("课程名称", name));
        root.add(pair("学分", credits, "总学时", hours));
        root.add(row("课程性质", nature));
        root.add(row("开课学院", department));
        JButton clear = AbstractCoursePanel.secondary("重新选择");
        clear.addActionListener(event -> clear());
        root.add(clear);
    }

    /** Returns the complete selector component. */
    public JPanel component() { return root; }

    /** Returns the currently chosen authoritative definition. */
    public CurriculumCourseCandidate requireSelection() {
        if (selection == null) throw new IllegalArgumentException("请先从匹配结果中选择课程");
        return selection;
    }

    /** Shows an existing catalog course as locked metadata. */
    public void showExisting(String courseCode, String courseName, String credit,
                             int totalHours, String owningDepartment) {
        code.setSelection("existing", courseCode);
        name.setSelection("existing", courseName);
        credits.setText(credit);
        hours.setText(String.valueOf(totalHours));
        nature.setText("由培养方案确定");
        department.setText(owningDepartment == null ? "" : owningDepartment);
        setInputsEnabled(false);
        root.getComponent(root.getComponentCount() - 1).setVisible(false);
    }

    private java.util.concurrent.CompletableFuture<List<AutocompleteChoice>> search(
            String query, int limit, boolean byCode) {
        return gateway.searchCurriculumCandidates(new CurriculumCourseCandidateQuery(query, 0, limit))
                .thenApply(page -> page.items().stream().filter(row -> !row.conflicted()).map(row -> {
                    candidates.put(row.planCourseId(), row);
                    String label = byCode ? row.courseCode() : row.courseName();
                    String detail = (byCode ? row.courseName() : row.courseCode()) + " · " + row.departmentName();
                    return new AutocompleteChoice(row.planCourseId(), label, detail);
                }).toList());
    }

    private void select(AutocompleteChoice choice) {
        CurriculumCourseCandidate value = candidates.get(choice.id());
        if (value == null) return;
        selection = value;
        code.setSelection(value.planCourseId(), value.courseCode());
        name.setSelection(value.planCourseId(), value.courseName());
        credits.setText(value.credits().toPlainString());
        hours.setText(String.valueOf(value.totalHours()));
        nature.setText(value.courseNature());
        department.setText(value.departmentName());
        setInputsEnabled(false);
    }

    private void clear() {
        selection = null;
        setInputsEnabled(true);
        code.inputComponent().setText("");
        name.inputComponent().setText("");
        for (JTextField field : List.of(credits, hours, nature, department)) field.setText("");
        code.inputComponent().requestFocusInWindow();
    }

    private void setInputsEnabled(boolean enabled) { code.setEnabled(enabled); name.setEnabled(enabled); }
    private static JPanel pair(String leftLabel, java.awt.Component left, String rightLabel, java.awt.Component right) {
        JPanel pair = new JPanel(new java.awt.GridLayout(1, 2, UiSpacing.SM, 0));
        pair.setOpaque(false); pair.add(row(leftLabel, left)); pair.add(row(rightLabel, right)); return pair;
    }
    private static JPanel row(String label, java.awt.Component input) { JPanel row=vertical();JLabel text=AbstractCoursePanel.label(label,UiTypography.BODY,UiColors.TEXT_PRIMARY);row.add(text);row.add(Box.createVerticalStrut(UiSpacing.XS));row.add(input);row.add(Box.createVerticalStrut(UiSpacing.SM));return row; }
    private static JPanel vertical(){JPanel panel=new JPanel();panel.setOpaque(false);panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));return panel;}
    private static JTextField locked(String name){JTextField field=new JTextField();field.setEditable(false);field.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));field.getAccessibleContext().setAccessibleName(name);return field;}
}
