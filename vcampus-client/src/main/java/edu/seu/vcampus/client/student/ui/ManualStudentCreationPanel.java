package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.*;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import java.awt.*;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Wide page-embedded editor for manually creating one student. */
public final class ManualStudentCreationPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
    private final ManualStudentFieldsPanel fields;
    private final StudentClientService students;
    private final Consumer<StudentAdmissionResult> completed;
    private final Runnable close;
    private final JLabel error = new JLabel(" ");
    private final JButton submit = new JButton("新增学生");
    private long generation;

    /** Creates the editor for a fixed department, major, and class. */
    public ManualStudentCreationPanel(StudentClientService students, DepartmentView department,
            MajorView major, ClassView studentClass, Consumer<StudentAdmissionResult> completed,
            Runnable close) {
        this.students = Objects.requireNonNull(students);
        this.completed = Objects.requireNonNull(completed);
        this.close = Objects.requireNonNull(close);
        fields = new ManualStudentFieldsPanel(Objects.requireNonNull(department),
                Objects.requireNonNull(major), Objects.requireNonNull(studentClass));
        build();
    }

    private void build() {
        root.setBackground(UiColors.BACKGROUND_PAGE); root.setBorder(UiBorders.pageInset());
        JScrollPane scroll = new JScrollPane(fields); scroll.setBorder(null);
        scroll.getViewport().setOpaque(false); scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16); root.add(scroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0)); bottom.setOpaque(false);
        error.setName("student.manual.error"); error.setForeground(UiColors.ERROR_FG);
        bottom.add(error, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        actions.setOpaque(false); JButton cancel = new JButton("取消");
        cancel.setName("student.manual.cancel"); cancel.addActionListener(event -> close.run());
        submit.setName("student.manual.submit"); submit.addActionListener(event -> submit());
        actions.add(cancel); actions.add(submit); bottom.add(actions, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);
    }

    private void submit() {
        CreateStudentManualCommand command;
        try { command = fields.command(); }
        catch (DateTimeException invalid) { error.setText("日期格式必须为 yyyy-MM-dd"); return; }
        List<StudentFieldError> errors = StudentFieldValidator.validateManual(command, LocalDate.now());
        if (!errors.isEmpty()) {
            StudentFieldError first = errors.getFirst(); error.setText(first.message());
            JComponent input = fields.input(first.field()); if (input != null) input.requestFocusInWindow();
            return;
        }
        long request = ++generation; setSaving(true);
        students.createManual(StudentFieldValidator.normalizeManual(command))
                .whenComplete((body, failure) -> SwingUtilities.invokeLater(() -> {
                    if (request != generation) return;
                    if (failure != null || body == null || !body.success() || body.data() == null) {
                        setSaving(false); error.setText(body != null && body.message() != null
                                ? body.message() : "新增失败，请稍后重试"); return;
                    }
                    completed.accept(body.data()); close.run();
                }));
    }

    private void setSaving(boolean saving) {
        fields.setInputsEnabled(!saving); submit.setEnabled(!saving);
        submit.setText(saving ? "正在新增…" : "新增学生"); if (saving) error.setText(" ");
    }
    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return fields.isDirty(); }
    @Override public void onClosed() { generation++; }
}
