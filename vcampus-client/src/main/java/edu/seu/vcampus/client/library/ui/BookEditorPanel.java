package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.core.ui.editor.*;

import javax.swing.*;

/** Embedded-editor adapter for the reusable book form card. */
final class BookEditorPanel implements EmbeddedEditor {
    private final BookFormCardPanel form;

    BookEditorPanel(BookFormCardPanel form) { this.form = form; }

    @Override public JComponent component() { return form; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return form.hasChanges(); }
}
