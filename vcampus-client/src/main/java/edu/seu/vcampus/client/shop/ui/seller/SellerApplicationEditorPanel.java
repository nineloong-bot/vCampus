package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.core.ui.editor.*;

import javax.swing.*;

/** Embedded-editor adapter for the existing seller application form. */
final class SellerApplicationEditorPanel implements EmbeddedEditor {
    private final SellerApplicationForms.Form form;

    SellerApplicationEditorPanel(SellerApplicationForms.Form form) { this.form = form; }
    @Override public JComponent component() { return form; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return form.dirty(); }
}
