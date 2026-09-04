package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Saves personal changes as a draft; it never writes the formal record directly. */
final class PersonalProfileEditDialog extends JDialog {
    PersonalProfileEditDialog(Window owner, StudentClientService students, StudentPersonalProfile personal,
            java.time.LocalDate enrollmentDate, long expectedVersion, Consumer<StudentProfileWorkspace> saved) {
        super(owner, "编辑个人基本信息", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        PersonalProfileEditPanel editor = new PersonalProfileEditPanel(personal, enrollmentDate);
        JScrollPane scroll = new JScrollPane(editor); scroll.setBorder(null); scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        JLabel error = new JLabel(" "); error.setForeground(UiColors.ERROR_FG);
        JButton cancel = new JButton("取消"); cancel.addActionListener(e -> dispose());
        JButton save = new JButton("暂存"); save.setName("student.profile.personal.save");
        save.addActionListener(e -> {
            StudentPersonalProfile value;
            try { value = editor.value(); } catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); return; }
            save.setEnabled(false); error.setText("正在暂存…");
            students.savePersonalDraft(new SaveStudentPersonalDraftCommand(value, expectedVersion))
                    .whenComplete((body, failure) -> SwingUtilities.invokeLater(() -> {
                        if (failure != null || body == null || !body.success() || body.data() == null) {
                            error.setText(message(body, "暂存失败，请稍后重试")); save.setEnabled(true); return;
                        }
                        saved.accept(body.data()); dispose();
                    }));
        });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        actions.setBackground(UiColors.BACKGROUND_PAGE); actions.add(error); actions.add(cancel); actions.add(save);
        add(actions, BorderLayout.SOUTH); setSize(620, 720); setLocationRelativeTo(owner);
    }

    private static String message(ResponseBody<?> body, String fallback) {
        return body != null && body.message() != null && !body.message().isBlank() ? body.message() : fallback;
    }
}
