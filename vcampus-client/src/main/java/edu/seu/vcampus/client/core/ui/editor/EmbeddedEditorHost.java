package edu.seu.vcampus.client.core.ui.editor;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/** Keeps a list stable while opening its editor inside the same page. */
public final class EmbeddedEditorHost extends JPanel {
    private static final int RIGHT_PLACEMENT_WIDTH = 1180;
    private final JComponent list;
    private final DiscardChangesConfirmation confirmation;
    private EmbeddedEditor editor;
    private JSplitPane split;
    private EditorPlacement placement;
    private int widthOverride = -1;
    private long generation;

    /** Creates a host using the standard discard confirmation. */
    public EmbeddedEditorHost(JComponent list) {
        this(list, DiscardChangesConfirmation.standard());
    }

    /** Creates a host with an injectable discard confirmation. */
    public EmbeddedEditorHost(JComponent list, DiscardChangesConfirmation confirmation) {
        super(new BorderLayout());
        this.list = Objects.requireNonNull(list, "list");
        this.confirmation = Objects.requireNonNull(confirmation, "confirmation");
        add(list, BorderLayout.CENTER);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { updatePlacement(); }
        });
    }

    /** Opens or replaces the current editor, returning false when discard is rejected. */
    public boolean showEditor(EmbeddedEditor next) {
        Objects.requireNonNull(next, "next");
        if (editor != null && editor.isDirty() && !confirmation.confirm(this)) return false;
        if (editor != null) detachEditor();
        editor = next;
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, list, next.component());
        split.setResizeWeight(0.68);
        split.setContinuousLayout(true);
        removeAll();
        add(split, BorderLayout.CENTER);
        generation++;
        updatePlacement();
        next.onOpened();
        revalidate();
        repaint();
        return true;
    }

    /** Builds an editor with completion and cancellation actions bound to that exact editor instance. */
    public boolean showEditor(BiFunction<Runnable, Runnable, EmbeddedEditor> factory) {
        Objects.requireNonNull(factory, "factory");
        AtomicReference<EmbeddedEditor> expected = new AtomicReference<>();
        EmbeddedEditor next = factory.apply(
                () -> completeAndClose(expected.get()),
                () -> requestClose(expected.get()));
        expected.set(next);
        return showEditor(next);
    }

    /** Requests closure, asking before dirty state is discarded. */
    public boolean requestClose() {
        if (editor == null) return true;
        if (editor.isDirty() && !confirmation.confirm(this)) return false;
        closeEditor();
        return true;
    }

    /** Requests closure only when the expected editor is still current. */
    public boolean requestClose(EmbeddedEditor expected) {
        return editor == expected && requestClose();
    }

    /** Closes an editor after a successful save or completed workflow. */
    public boolean completeAndClose() {
        if (editor == null) return false;
        closeEditor();
        return true;
    }

    /** Completes only the expected editor, rejecting a stale asynchronous callback. */
    public boolean completeAndClose(EmbeddedEditor expected) {
        return editor == expected && completeAndClose();
    }

    /** Returns whether an editor is currently visible. */
    public boolean isEditorOpen() { return editor != null; }

    /** Returns the current resolved placement, or null while closed. */
    public EditorPlacement currentPlacement() { return placement; }

    /** Returns the generation used to reject stale asynchronous callbacks. */
    public long generation() { return generation; }

    /** Returns whether a captured generation still belongs to the current state. */
    public boolean isCurrent(long candidate) { return generation == candidate; }

    /** Returns whether the supplied editor instance is still the visible editor. */
    public boolean isCurrent(EmbeddedEditor candidate) { return editor == candidate; }

    void setAvailableWidthForTest(int width) {
        widthOverride = width;
        updatePlacement();
    }

    private void closeEditor() {
        detachEditor();
        removeAll();
        add(list, BorderLayout.CENTER);
        placement = null;
        split = null;
        revalidate();
        repaint();
    }

    private void detachEditor() {
        EmbeddedEditor previous = editor;
        editor = null;
        generation++;
        previous.onClosed();
    }

    private void updatePlacement() {
        if (editor == null || split == null) return;
        EditorPlacement next = resolvePlacement();
        if (next == placement) return;
        placement = next;
        split.setOrientation(next == EditorPlacement.RIGHT
                ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT);
        split.setDividerLocation(next == EditorPlacement.RIGHT ? 0.68 : 0.55);
    }

    private EditorPlacement resolvePlacement() {
        if (editor.size() == EditorSize.WIDE) return EditorPlacement.BOTTOM;
        int available = widthOverride >= 0 ? widthOverride : getWidth();
        Dimension listMinimum = list.getMinimumSize();
        Dimension editorMinimum = editor.component().getMinimumSize();
        int required = listMinimum.width + editorMinimum.width + 16;
        return available >= RIGHT_PLACEMENT_WIDTH && available >= required
                ? EditorPlacement.RIGHT : EditorPlacement.BOTTOM;
    }
}
