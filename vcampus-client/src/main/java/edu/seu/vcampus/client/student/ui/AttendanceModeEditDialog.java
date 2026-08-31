package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Academic draft editor restricted to attendance mode. */
final class AttendanceModeEditDialog extends JDialog {
    AttendanceModeEditDialog(Window owner, StudentClientService students, AttendanceMode current,
            long expectedVersion, Consumer<StudentProfileWorkspace> saved) {
        super(owner, "编辑学籍信息", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        AttendanceModeEditPanel editor = new AttendanceModeEditPanel(current); add(editor, BorderLayout.CENTER);
        JLabel error = new JLabel(" "); error.setForeground(UiColors.ERROR_FG);
        JButton cancel = new JButton("取消"); cancel.addActionListener(e -> dispose());
        JButton save = new JButton("暂存"); save.setName("student.profile.attendance.save");
        save.addActionListener(e -> {
            save.setEnabled(false); error.setText("正在暂存…");
            students.saveAttendanceDraft(new SaveStudentAttendanceDraftCommand(editor.selectedMode(), expectedVersion))
                    .whenComplete((body, failure) -> SwingUtilities.invokeLater(() -> {
                        if (failure != null || body == null || !body.success() || body.data() == null) {
                            error.setText(body != null && body.message() != null ? body.message() : "暂存失败，请稍后重试");
                            save.setEnabled(true); return;
                        }
                        saved.accept(body.data()); dispose();
                    }));
        });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        actions.setBackground(UiColors.BACKGROUND_PAGE); actions.add(error); actions.add(cancel); actions.add(save);
        add(actions, BorderLayout.SOUTH); pack(); setMinimumSize(new Dimension(480, 190)); setLocationRelativeTo(owner);
    }
}
