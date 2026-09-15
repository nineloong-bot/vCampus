package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.core.ui.editor.*;

import javax.swing.*;

/** Compact embedded adapter for seller shop profile fields. */
final class ShopProfileEditorPanel implements EmbeddedEditor {
    private final JComponent component;
    private final ShopProfilePanel owner;
    ShopProfileEditorPanel(JComponent component, ShopProfilePanel owner) {
        this.component = component; this.owner = owner;
    }
    @Override public JComponent component() { return component; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return owner.hasChanges(); }
}
