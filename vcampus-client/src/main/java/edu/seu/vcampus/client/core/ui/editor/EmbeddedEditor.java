package edu.seu.vcampus.client.core.ui.editor;

import javax.swing.JComponent;

/** Lifecycle contract implemented by a page-embedded data editor. */
public interface EmbeddedEditor {
    /** Returns the stable Swing component displayed by the host. */
    JComponent component();

    /** Returns the editor's preferred space category. */
    EditorSize size();

    /** Reports whether closing the editor may discard user changes. */
    boolean isDirty();

    /** Called after the component is attached to a host. */
    default void onOpened() { }

    /** Called after the component is detached from a host. */
    default void onClosed() { }
}
