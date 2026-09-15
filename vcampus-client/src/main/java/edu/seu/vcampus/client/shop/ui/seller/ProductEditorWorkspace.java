package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.core.ui.editor.*;
import edu.seu.vcampus.common.shop.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Wide product editor with page-level save and cancel actions. */
public final class ProductEditorWorkspace implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final ProductEditorPanel editor;

    /** Creates an embedded product editor for create or update mode. */
    public ProductEditorWorkspace(ProductEditorPanel editor, boolean creating,
            Consumer<CreateProductCommand> create, Consumer<UpdateProductCommand> update, Runnable close) {
        this.editor = editor; root.add(editor);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("取消"); cancel.addActionListener(event -> close.run());
        JButton save = new JButton(creating ? "创建商品" : "保存修改");
        save.addActionListener(event -> {
            if (creating) create.accept(editor.createCommand()); else update.accept(editor.updateCommand());
        });
        actions.add(cancel); actions.add(save); root.add(actions, BorderLayout.SOUTH);
    }
    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return editor.hasChanges(); }
}
