package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.editor.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.majortransfer.*;

import javax.swing.*;
import java.awt.*;

/** Embedded editor for choosing the destination class and executing a transfer. */
final class MajorTransferExecutionPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JComboBox<ClassView> classes = new JComboBox<>();
    private final JLabel status = new JLabel("正在加载目标班级…");

    MajorTransferExecutionPanel(StudentClientService students, MajorTransferApplicationView app,
            Runnable completed, Runnable close) {
        JPanel form = new JPanel(new BorderLayout(8, 8));
        form.add(new JLabel("目标班级："), BorderLayout.WEST); form.add(classes); root.add(form);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("取消"); cancel.addActionListener(event -> close.run());
        JButton execute = new JButton("确认执行"); execute.setEnabled(false);
        execute.addActionListener(event -> execute(students, app, execute, completed, close));
        actions.add(status); actions.add(cancel); actions.add(execute); root.add(actions, BorderLayout.SOUTH);
        students.listClasses(app.targetMajorId()).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (failure != null || response == null || !response.success() || response.data().isEmpty()) {
                        status.setText("目标班级加载失败"); return;
                    }
                    response.data().forEach(classes::addItem); status.setText(" "); execute.setEnabled(true);
                }));
    }

    private void execute(StudentClientService students, MajorTransferApplicationView app,
            JButton button, Runnable completed, Runnable close) {
        ClassView selected = (ClassView) classes.getSelectedItem();
        if (selected == null) { status.setText("请选择目标班级"); return; }
        button.setEnabled(false); status.setText("正在执行…");
        students.executeTransfer(new ExecuteMajorTransferCommand(app.applicationId(),
                selected.classId(), app.applicationVersion())).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (failure != null || response == null || !response.success()) {
                        status.setText(response != null ? response.message() : "执行失败");
                        button.setEnabled(true); return;
                    }
                    completed.run(); close.run();
                }));
    }
    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return false; }
}
