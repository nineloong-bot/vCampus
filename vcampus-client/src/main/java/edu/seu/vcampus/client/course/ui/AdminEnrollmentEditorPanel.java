package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;

import javax.swing.JComponent;

/** Embedded-editor adapter for administrator-managed enrollment. */
final class AdminEnrollmentEditorPanel implements EmbeddedEditor {
    private final AdminEnrollmentControl control;

    AdminEnrollmentEditorPanel(AdminEnrollmentControl control) { this.control = control; }

    @Override public JComponent component() { return control; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return control.isDirty(); }
    @Override public void onOpened() { control.activate(); }
    @Override public void onClosed() { control.deactivate(); }
}
