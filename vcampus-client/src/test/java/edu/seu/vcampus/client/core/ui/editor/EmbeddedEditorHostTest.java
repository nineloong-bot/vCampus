package edu.seu.vcampus.client.core.ui.editor;

import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedEditorHostTest {
    @Test
    void startsClosedAndKeepsListBesideCompactEditor() {
        JPanel list = new JPanel();
        AtomicBoolean confirm = new AtomicBoolean(false);
        EmbeddedEditorHost host = new EmbeddedEditorHost(list, owner -> confirm.get());
        RecordingEditor editor = new RecordingEditor(EditorSize.COMPACT, true);

        assertThat(host.isEditorOpen()).isFalse();
        assertThat(host.getComponentCount()).isEqualTo(1);
        assertThat(host.getComponent(0)).isSameAs(list);

        host.setAvailableWidthForTest(1400);
        host.showEditor(editor);
        assertThat(host.getComponentCount()).isEqualTo(1);
        JSplitPane split = (JSplitPane) host.getComponent(0);
        assertThat(split.getOrientation()).isEqualTo(JSplitPane.HORIZONTAL_SPLIT);
        assertThat(split.getLeftComponent()).isSameAs(list);
        assertThat(split.getRightComponent()).isSameAs(editor.component());
        assertThat(editor.opened).isEqualTo(1);

        host.setAvailableWidthForTest(900);
        split = (JSplitPane) host.getComponent(0);
        assertThat(split.getOrientation()).isEqualTo(JSplitPane.VERTICAL_SPLIT);
        assertThat(split.getTopComponent()).isSameAs(list);
        assertThat(split.getBottomComponent()).isSameAs(editor.component());
        assertThat(editor.opened).isEqualTo(1);

        assertThat(host.requestClose()).isFalse();
        assertThat(host.isEditorOpen()).isTrue();
        assertThat(editor.closed).isZero();

        assertThat(host.completeAndClose()).isTrue();
        assertThat(host.isEditorOpen()).isFalse();
        assertThat(host.getComponent(0)).isSameAs(list);
        assertThat(editor.closed).isEqualTo(1);
    }

    @Test
    void wideEditorAppearsBelowListAndDirtyReplacementNeedsConfirmation() {
        JPanel list = new JPanel();
        AtomicBoolean confirm = new AtomicBoolean(false);
        EmbeddedEditorHost host = new EmbeddedEditorHost(list, owner -> confirm.get());
        RecordingEditor first = new RecordingEditor(EditorSize.COMPACT, true);
        RecordingEditor wide = new RecordingEditor(EditorSize.WIDE, false);
        host.setAvailableWidthForTest(1600);
        host.showEditor(first);
        long firstGeneration = host.generation();

        assertThat(host.showEditor(wide)).isFalse();
        assertThat(host.generation()).isEqualTo(firstGeneration);
        assertThat(first.closed).isZero();
        assertThat(wide.opened).isZero();

        confirm.set(true);
        assertThat(host.showEditor(wide)).isTrue();
        JSplitPane split = (JSplitPane) host.getComponent(0);
        assertThat(split.getOrientation()).isEqualTo(JSplitPane.VERTICAL_SPLIT);
        assertThat(split.getTopComponent()).isSameAs(list);
        assertThat(split.getBottomComponent()).isSameAs(wide.component());
        assertThat(first.closed).isEqualTo(1);
        assertThat(wide.opened).isEqualTo(1);
        assertThat(host.isCurrent(firstGeneration)).isFalse();
        assertThat(host.isCurrent(host.generation())).isTrue();
    }

    @Test
    void staleCompletionCannotCloseReplacementEditor() {
        EmbeddedEditorHost host = new EmbeddedEditorHost(new JPanel(), owner -> true);
        AtomicReference<Runnable> firstCompletion = new AtomicReference<>();
        RecordingEditor second = new RecordingEditor(EditorSize.COMPACT, false);
        host.showEditor((complete, cancel) -> {
            firstCompletion.set(complete);
            return new RecordingEditor(EditorSize.COMPACT, false);
        });
        host.showEditor(second);

        firstCompletion.get().run();

        assertThat(host.isEditorOpen()).isTrue();
        assertThat(second.closed).isZero();
    }

    private static final class RecordingEditor implements EmbeddedEditor {
        private final JComponent component = new JLabel("editor");
        private final EditorSize size;
        private final boolean dirty;
        private int opened;
        private int closed;

        private RecordingEditor(EditorSize size, boolean dirty) {
            this.size = size;
            this.dirty = dirty;
        }

        @Override public JComponent component() { return component; }
        @Override public EditorSize size() { return size; }
        @Override public boolean isDirty() { return dirty; }
        @Override public void onOpened() { opened++; }
        @Override public void onClosed() { closed++; }
    }
}
