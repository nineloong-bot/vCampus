package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;

import javax.swing.JComponent;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Adapts the training-plan course form to the shared wide embedded workspace. */
public final class TrainingPlanCourseEditorPanel implements EmbeddedEditor {
    private final JComponent component;
    private final BooleanSupplier dirty;

    /** Creates an adapter for the existing course form and its dirty-state supplier. */
    public TrainingPlanCourseEditorPanel(JComponent component, BooleanSupplier dirty) {
        this.component = Objects.requireNonNull(component);
        this.dirty = Objects.requireNonNull(dirty);
    }

    @Override public JComponent component() { return component; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return dirty.getAsBoolean(); }
}
