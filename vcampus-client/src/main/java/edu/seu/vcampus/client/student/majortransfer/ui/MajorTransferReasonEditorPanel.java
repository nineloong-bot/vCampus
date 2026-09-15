package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.editor.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Embedded editor for mandatory transfer review or cancellation reasons. */
final class MajorTransferReasonEditorPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JTextArea reason = new JTextArea(4, 30);
    private final JLabel status = new JLabel(" ");

    MajorTransferReasonEditorPanel(String title, Consumer<String> submit, Runnable close) {
        root.add(new JLabel(title + "："), BorderLayout.NORTH);
        reason.setLineWrap(true); reason.setWrapStyleWord(true);
        root.add(new JScrollPane(reason), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("取消"); cancel.addActionListener(event -> close.run());
        JButton confirm = new JButton("确认"); confirm.addActionListener(event -> {
            String value = reason.getText().trim();
            if (value.isBlank()) { status.setText(title + "不能为空"); return; }
            submit.accept(value);
        });
        actions.add(status); actions.add(cancel); actions.add(confirm); root.add(actions, BorderLayout.SOUTH);
    }
    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return !reason.getText().isBlank(); }
}
