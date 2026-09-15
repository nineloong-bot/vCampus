package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.GradeResult;
import edu.seu.vcampus.common.student.RecordStudentGradeCommand;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/** Page-embedded form for recording one student's course result. */
public final class GradeEntryPanel implements EmbeddedEditor {
    private static final String DEFAULT_SEMESTER = "2024-2025-1";
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JTextField planCourseId = new JTextField(20);
    private final JComboBox<GradeResult> result = new JComboBox<>(GradeResult.values());
    private final JTextField semester = new JTextField(DEFAULT_SEMESTER, 12);
    private final JLabel status = new JLabel(" ");
    private boolean active;

    /** Creates an editor bound to a student and completion callback. */
    public GradeEntryPanel(StudentClientService students, String studentId,
            Runnable completed, Runnable close) {
        Objects.requireNonNull(students, "students");
        Objects.requireNonNull(studentId, "studentId");
        JPanel form = new JPanel(new GridBagLayout());
        addField(form, 0, "方案课程ID", planCourseId);
        addField(form, 1, "结果", result);
        addField(form, 2, "修读学期", semester);
        root.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("取消");
        cancel.addActionListener(event -> close.run());
        JButton submit = new JButton("确认录入");
        submit.addActionListener(event -> submit(students, studentId, submit, completed, close));
        actions.add(status);
        actions.add(cancel);
        actions.add(submit);
        root.add(actions, BorderLayout.SOUTH);
    }

    private void submit(StudentClientService students, String studentId, JButton button,
            Runnable completed, Runnable close) {
        String courseId = planCourseId.getText().trim();
        String term = semester.getText().trim();
        if (courseId.isBlank() || term.isBlank()) {
            status.setText("方案课程ID和修读学期不能为空");
            return;
        }
        button.setEnabled(false);
        status.setText("正在录入…");
        students.recordGrade(new RecordStudentGradeCommand(studentId, courseId,
                (GradeResult) result.getSelectedItem(), term))
                .whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!active) return;
                    if (failure != null || response == null || !response.success()) {
                        status.setText(response != null && response.message() != null
                                ? response.message() : "成绩录入失败");
                        button.setEnabled(true);
                        return;
                    }
                    completed.run();
                    close.run();
                }));
    }

    private static void addField(JPanel form, int row, String label, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        form.add(new JLabel(label + "："), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public void onOpened() { active = true; }
    @Override public void onClosed() { active = false; }
    @Override public boolean isDirty() {
        return !planCourseId.getText().isBlank()
                || result.getSelectedIndex() != 0
                || !DEFAULT_SEMESTER.equals(semester.getText());
    }
}
