package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.editor.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.*;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

/** Embedded editor for recording transfer assessment scores. */
final class MajorTransferScoreEditorPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JTextField written = new JTextField(8);
    private final JTextField interview = new JTextField(8);
    private final JLabel status = new JLabel(" ");

    MajorTransferScoreEditorPanel(StudentClientService students, MajorTransferApplicationView app,
            Runnable completed, Runnable close) {
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("笔试成绩：")); form.add(written);
        form.add(new JLabel("面试成绩：")); form.add(interview); root.add(form);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("取消"); cancel.addActionListener(event -> close.run());
        JButton submit = new JButton("确认录入");
        submit.addActionListener(event -> submit(students, app, submit, completed, close));
        actions.add(status); actions.add(cancel); actions.add(submit); root.add(actions, BorderLayout.SOUTH);
    }

    private void submit(StudentClientService students, MajorTransferApplicationView app,
            JButton button, Runnable completed, Runnable close) {
        try {
            BigDecimal first = decimal(written); BigDecimal second = decimal(interview);
            button.setEnabled(false); status.setText("正在录入…");
            students.recordTransferScore(new RecordMajorTransferScoreCommand(app.applicationId(),
                    first, second, app.applicationVersion())).whenComplete((response, failure) ->
                    SwingUtilities.invokeLater(() -> {
                        if (failure != null || response == null || !response.success()) {
                            status.setText(response != null ? response.message() : "录入失败");
                            button.setEnabled(true); return;
                        }
                        completed.run(); close.run();
                    }));
        } catch (NumberFormatException error) { status.setText("成绩请输入数字"); }
    }

    private static BigDecimal decimal(JTextField field) {
        return field.getText().isBlank() ? null : new BigDecimal(field.getText().trim());
    }
    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return !written.getText().isBlank() || !interview.getText().isBlank(); }
}
