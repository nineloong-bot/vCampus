package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;

import javax.swing.JComponent;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Adapts the training-plan form to the shared wide embedded workspace. */
public final class TrainingPlanEditorPanel implements EmbeddedEditor {
    private final JComponent component;
    private final BooleanSupplier dirty;

    /** Creates an adapter for the existing plan form and its dirty-state supplier. */
    public TrainingPlanEditorPanel(JComponent component, BooleanSupplier dirty) {
        this.component = Objects.requireNonNull(component);
        this.dirty = Objects.requireNonNull(dirty);
    }

    @Override public JComponent component() { return component; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return dirty.getAsBoolean(); }
}
