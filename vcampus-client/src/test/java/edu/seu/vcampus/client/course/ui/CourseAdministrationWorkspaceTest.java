package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.editor.EditorPlacement;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseAdministrationWorkspaceTest {
    @Test
    void catalogAndTermEditorsOpenOnlyAfterAnExplicitAction() throws Exception {
        CourseCatalogPanel catalog = onEdt(() -> new CourseCatalogPanel(CourseUiGateway.preview()));
        TermManagementPanel terms = onEdt(() -> new TermManagementPanel(CourseUiGateway.preview()));
        flushEdt();

        EmbeddedEditorHost catalogHost = descendant(catalog, EmbeddedEditorHost.class);
        EmbeddedEditorHost termHost = descendant(terms, EmbeddedEditorHost.class);
        assertThat(catalogHost.isEditorOpen()).isFalse();
        assertThat(termHost.isEditorOpen()).isFalse();

        onEdt(() -> button(catalog, "新建课程").doClick());
        onEdt(() -> button(terms, "新建学期").doClick());

        assertThat(catalogHost.isEditorOpen()).isTrue();
        assertThat(termHost.isEditorOpen()).isTrue();
        catalogHost.setSize(1400, 800);
        termHost.setSize(1400, 800);
        flushEdt();
        assertThat(catalogHost.currentPlacement()).isEqualTo(EditorPlacement.RIGHT);
        assertThat(termHost.currentPlacement()).isEqualTo(EditorPlacement.RIGHT);
    }

    @Test
    void phaseAndOutcomeEditorsAreHiddenUntilRequested() throws Exception {
        SelectionPhaseManagementPanel phases = onEdt(
                () -> new SelectionPhaseManagementPanel(CourseUiGateway.preview()));
        OutcomeImportPanel outcomes = onEdt(() -> new OutcomeImportPanel(CourseUiGateway.preview()));
        flushEdt();

        EmbeddedEditorHost phaseHost = descendant(phases, EmbeddedEditorHost.class);
        EmbeddedEditorHost outcomeHost = descendant(outcomes, EmbeddedEditorHost.class);
        assertThat(phaseHost.isEditorOpen()).isFalse();
        assertThat(outcomeHost.isEditorOpen()).isFalse();

        onEdt(() -> button(phases, "新建阶段").doClick());
        onEdt(() -> button(outcomes, "导入课程结果").doClick());

        assertThat(phaseHost.isEditorOpen()).isTrue();
        assertThat(outcomeHost.isEditorOpen()).isTrue();
        assertThat(outcomeHost.currentPlacement()).isEqualTo(EditorPlacement.BOTTOM);
    }

    private static JButton button(Container root, String text) {
        return descendants(root).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static <T extends Component> T descendant(Container root, Class<T> type) {
        return descendants(root).stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static List<Component> descendants(Container root) {
        List<Component> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            result.add(child);
            if (child instanceof Container nested) result.addAll(descendants(nested));
        }
        return result;
    }

    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }

    private static <T> T onEdt(java.util.concurrent.Callable<T> work) throws Exception {
        java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(work.call()); } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        return result.get();
    }

    private static void onEdt(Runnable work) throws Exception { SwingUtilities.invokeAndWait(work); }
}
