package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.editor.EditorPlacement;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseAdministrationWorkspaceTest {
    @Test
    void catalogIsReadOnlyAndTermEditorOpensOnlyAfterAnExplicitAction() throws Exception {
        CourseCatalogPanel catalog = onEdt(() -> new CourseCatalogPanel(CourseUiGateway.preview()));
        TermManagementPanel terms = onEdt(() -> new TermManagementPanel(CourseUiGateway.preview()));
        flushEdt();

        EmbeddedEditorHost termHost = descendant(terms, EmbeddedEditorHost.class);
        assertThat(termHost.isEditorOpen()).isFalse();
        assertThat(columns(descendant(catalog, JTable.class)))
                .containsExactly("课程代码", "课程名称", "学分", "总学时", "状态", "开课学院");
        assertThat(columns(descendant(terms, JTable.class)))
                .containsExactly("学期代码", "学期名称", "开学日期", "结束日期", "状态");

        assertThat(descendants(catalog).stream().filter(JButton.class::isInstance)
                .map(JButton.class::cast).map(JButton::getText))
                .doesNotContain("新建课程", "编辑所选");
        onEdt(() -> button(terms, "新建学期").doClick());

        assertThat(termHost.isEditorOpen()).isTrue();
        termHost.setSize(1400, 800);
        flushEdt();
        assertThat(termHost.currentPlacement()).isEqualTo(EditorPlacement.RIGHT);
    }

    @Test
    void phaseEditorIsHiddenUntilRequested() throws Exception {
        SelectionPhaseManagementPanel phases = onEdt(
                () -> new SelectionPhaseManagementPanel(CourseUiGateway.preview()));
        flushEdt();

        EmbeddedEditorHost phaseHost = descendant(phases, EmbeddedEditorHost.class);
        assertThat(phaseHost.isEditorOpen()).isFalse();

        onEdt(() -> button(phases, "新建阶段").doClick());

        assertThat(phaseHost.isEditorOpen()).isTrue();
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

    private static List<String> columns(JTable table) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < table.getColumnCount(); index++) {
            result.add(table.getColumnName(index));
        }
        return result;
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> work) throws Exception {
        java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(work.call()); } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        return result.get();
    }

    private static void onEdt(Runnable work) throws Exception { SwingUtilities.invokeAndWait(work); }
}
