package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;

import javax.swing.*;
import java.awt.*;

/** Wide embedded wrapper for the transfer-batch form card. */
final class MajorTransferBatchEditorPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout());
    private final MajorTransferBatchFormCardPanel form;

    MajorTransferBatchEditorPanel(MajorTransferBatchFormCardPanel form, Runnable close) {
        this.form = form;
        root.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("关闭工作区");
        cancel.addActionListener(event -> close.run());
        actions.add(cancel);
        root.add(actions, BorderLayout.SOUTH);
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return form.hasChanges(); }
}
