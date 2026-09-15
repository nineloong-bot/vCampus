package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;

import javax.swing.JComponent;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Wide embedded adapter for cross-discipline request and review forms. */
public final class CrossDisciplineRequestPanel implements EmbeddedEditor {
    private final JComponent component;
    private final BooleanSupplier dirty;

    /** Creates the adapter for a request or review component. */
    public CrossDisciplineRequestPanel(JComponent component, BooleanSupplier dirty) {
        this.component = Objects.requireNonNull(component);
        this.dirty = Objects.requireNonNull(dirty);
    }

    @Override public JComponent component() { return component; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return dirty.getAsBoolean(); }
}
