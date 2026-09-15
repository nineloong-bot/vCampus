package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.AttendanceMode;
import edu.seu.vcampus.common.student.SaveStudentAttendanceDraftCommand;
import edu.seu.vcampus.common.student.StudentProfileWorkspace;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.Consumer;

/** Adapts the attendance-mode field into an embedded draft editor. */
final class AttendanceModeDraftEditor implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout());
    private final AttendanceModeEditPanel form;
    private final AttendanceMode initial;
    private final JLabel status = new JLabel(" ");
    private final JButton save = new JButton("暂存");

    AttendanceModeDraftEditor(StudentClientService students, AttendanceMode current,
            long expectedVersion, Consumer<StudentProfileWorkspace> saved, Runnable close) {
        form = new AttendanceModeEditPanel(current);
        initial = form.selectedMode();
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2,
                UiSpacing.SPACE_2));
        actions.setOpaque(false);
        status.setForeground(UiColors.ERROR_FG);
        JButton cancel = new JButton("取消");
        cancel.addActionListener(event -> close.run());
        save.setName("student.profile.attendance.save");
        save.addActionListener(event -> submit(students, expectedVersion, saved, close));
        actions.add(status); actions.add(cancel); actions.add(save);
        root.add(actions, BorderLayout.SOUTH);
    }

    private void submit(StudentClientService students, long expectedVersion,
            Consumer<StudentProfileWorkspace> saved, Runnable close) {
        save.setEnabled(false);
        status.setText("正在暂存…");
        students.saveAttendanceDraft(new SaveStudentAttendanceDraftCommand(
                        form.selectedMode(), expectedVersion))
                .whenComplete((body, failure) -> SwingUtilities.invokeLater(() -> {
                    if (failure != null || body == null || !body.success() || body.data() == null) {
                        status.setText(body != null && body.message() != null
                                ? body.message() : "暂存失败，请稍后重试");
                        save.setEnabled(true);
                        return;
                    }
                    saved.accept(body.data());
                    close.run();
                }));
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return !Objects.equals(initial, form.selectedMode()); }
}
