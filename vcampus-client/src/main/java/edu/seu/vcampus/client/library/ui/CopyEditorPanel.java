package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.core.ui.editor.*;
import edu.seu.vcampus.common.library.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Compact page-embedded editor for adding or changing a library copy. */
public final class CopyEditorPanel implements EmbeddedEditor {
    /** Values entered while adding a copy. */
    public record AddValues(String isbn, String barcode, String location) { }

    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JTextField isbn = new JTextField(16);
    private final JTextField barcode = new JTextField(16);
    private final JTextField location = new JTextField(16);
    private JComboBox<CopyStatus> targetStatus;
    private final JLabel status = new JLabel(" ");

    /** Creates an add-copy editor, omitting ISBN when a book is already selected. */
    public static CopyEditorPanel add(BookSummary book, Consumer<AddValues> submit, Runnable close) {
        CopyEditorPanel panel = new CopyEditorPanel();
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        if (book == null) panel.field(form, "ISBN", panel.isbn);
        else {
            panel.field(form, "书目", new JLabel(book.title()));
            panel.field(form, "ISBN", new JLabel(book.isbn()));
        }
        panel.field(form, "馆藏条码", panel.barcode);
        panel.field(form, "馆藏位置", panel.location);
        panel.finish(form, "确认新增", () -> {
            AddValues values = new AddValues(panel.isbn.getText().trim(),
                    panel.barcode.getText().trim(), panel.location.getText().trim());
            if (values.barcode().isBlank() || values.location().isBlank()
                    || (book == null && values.isbn().isBlank())) {
                panel.status.setText("请填写 ISBN、馆藏条码和馆藏位置"); return;
            }
            submit.accept(values);
        }, close);
        return panel;
    }

    /** Creates an editor for changing one copy's status. */
    public static CopyEditorPanel status(BookCopyView copy, CopyStatus[] targets,
            Consumer<ChangeCopyStatusCommand> submit, Runnable close) {
        CopyEditorPanel panel = new CopyEditorPanel();
        panel.targetStatus = new JComboBox<>(targets);
        panel.targetStatus.setRenderer((list, value, index, selected, focused) -> {
            JLabel label = (JLabel) new DefaultListCellRenderer().getListCellRendererComponent(
                    list, value, index, selected, focused);
            label.setText(value == null ? "" : LibraryStatusText.copy(value)); return label;
        });
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        panel.field(form, "馆藏条码", new JLabel(copy.barcode()));
        panel.field(form, "目标状态", panel.targetStatus);
        panel.finish(form, "确认变更", () -> submit.accept(new ChangeCopyStatusCommand(
                copy.copyId(), (CopyStatus) panel.targetStatus.getSelectedItem(), copy.rowVersion())), close);
        return panel;
    }

    private CopyEditorPanel() {
        isbn.setName("library.copy-editor.isbn");
        barcode.setName("library.copy-editor.barcode");
        location.setName("library.copy-editor.location");
    }

    private void finish(JPanel form, String action, Runnable submit, Runnable close) {
        root.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("取消"); cancel.addActionListener(event -> close.run());
        JButton confirm = new JButton(action); confirm.addActionListener(event -> submit.run());
        actions.add(status); actions.add(cancel); actions.add(confirm); root.add(actions, BorderLayout.SOUTH);
    }

    private void field(JPanel form, String label, Component component) {
        form.add(new JLabel(label + "：")); form.add(component);
    }
    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() {
        return !isbn.getText().isBlank() || !barcode.getText().isBlank() || !location.getText().isBlank()
                || targetStatus != null && targetStatus.getSelectedIndex() > 0;
    }
}
