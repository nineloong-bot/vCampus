package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.editor.*;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfferingManagementWorkspaceTest {
    @Test
    void offeringAndManualRetakeEditorsAreHiddenUntilRequested() throws Exception {
        OfferingManagementPanel panel = onEdt(() -> new OfferingManagementPanel(CourseUiGateway.preview()));
        flush();
        EmbeddedEditorHost host = descendants(panel).stream().filter(EmbeddedEditorHost.class::isInstance)
                .map(EmbeddedEditorHost.class::cast).findFirst().orElseThrow();
        assertThat(host.isEditorOpen()).isFalse();

        onEdt(() -> button(panel, "新建教学班").doClick());
        assertThat(host.isEditorOpen()).isTrue();
        assertThat(host.currentPlacement()).isEqualTo(EditorPlacement.BOTTOM);
        onEdt(host::completeAndClose);

        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast)
                .findFirst().orElseThrow();
        onEdt(() -> table.setRowSelectionInterval(0, 0));
        onEdt(() -> button(panel, "添加重修学生").doClick());
        assertThat(host.isEditorOpen()).isTrue();
    }

    private static JButton button(Container root, String text) { return descendants(root).stream()
            .filter(JButton.class::isInstance).map(JButton.class::cast).filter(value -> text.equals(value.getText()))
            .findFirst().orElseThrow(); }
    private static List<Component> descendants(Container root) { List<Component> all = new ArrayList<>();
        for (Component child : root.getComponents()) { all.add(child); if (child instanceof Container nested) all.addAll(descendants(nested)); } return all; }
    private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }
    private static <T> T onEdt(java.util.concurrent.Callable<T> work) throws Exception { java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { result.set(work.call()); } catch (Exception failure) { throw new RuntimeException(failure); } }); return result.get(); }
    private static void onEdt(Runnable work) throws Exception { SwingUtilities.invokeAndWait(work); }
}
