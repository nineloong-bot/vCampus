package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.core.ui.editor.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Compact embedded editor for a required shop administration reason. */
public final class ShopReasonEditorPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JTextArea reason = new JTextArea(4, 24);
    private final JLabel status = new JLabel(" ");

    /** Creates a reason form with explicit submit and cancel actions. */
    public ShopReasonEditorPanel(String label, Consumer<String> submit, Runnable close) {
        root.add(new JLabel(label + "："), BorderLayout.NORTH);
        reason.setLineWrap(true); reason.setWrapStyleWord(true); root.add(new JScrollPane(reason));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("取消"); cancel.addActionListener(event -> close.run());
        JButton confirm = new JButton("确认"); confirm.addActionListener(event -> {
            String value = reason.getText().strip();
            if (value.isBlank()) { status.setText(label + "不能为空"); return; }
            submit.accept(value);
        });
        actions.add(status); actions.add(cancel); actions.add(confirm); root.add(actions, BorderLayout.SOUTH);
    }
    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return !reason.getText().isBlank(); }
}
