package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LibraryLayoutTest {
    @Test void managedBookPaginationFitsInTheLeftPane() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new BookManagementPanel(mock(LibraryClientService.class));
            LibraryUiStyle.apply(panel); panel.setSize(320, 540); layout(panel);
            assertControlsVisible(panel, panel);
        });
    }

    @Test void readerSplitKeepsBothPanesUsableAtMinimumWindowWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var workspace = new LibraryWorkspacePanel(mock(LibraryClientService.class), java.util.Set.of());
            workspace.setSize(840, 570); layout(workspace);
            var split = findSplit(workspace);
            assertThat(split.getLeftComponent().getWidth()).isGreaterThanOrEqualTo(320);
            assertThat(split.getRightComponent().getWidth()).isGreaterThanOrEqualTo(280);
            assertControlsVisible((Container) split.getLeftComponent(), (Container) split.getLeftComponent());
        });
    }

    private static JSplitPane findSplit(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JSplitPane split) return split;
            if (child instanceof Container nested) { var found = findSplit(nested); if (found != null) return found; }
        }
        return null;
    }

    @Test void adminActionsStayVisibleAtMinimumWindowWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new LoanAdminPanel(mock(LibraryClientService.class));
            LibraryUiStyle.apply(panel); panel.setSize(800, 540); layout(panel);
            assertControlsVisible(panel, panel);
        });
    }

    @Test void catalogFiltersStayVisibleInsideTheLeftColumn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new BookSearchPanel(mock(LibraryClientService.class));
            LibraryUiStyle.apply(panel); panel.setSize(400, 540); layout(panel);
            assertControlsVisible(panel, panel);
        });
    }

    @Test void loanColumnsRemainReadableAndCanScrollToTheFinalFineColumn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new LoanAdminPanel(mock(LibraryClientService.class));
            LibraryUiStyle.apply(panel); panel.setSize(800, 540); layout(panel);
            assertThat(panel.table.getColumnModel().getColumn(3).getWidth()).isGreaterThanOrEqualTo(180);
            var scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, panel.table);
            assertThat(scroll.getHorizontalScrollBar().isVisible()).isTrue();
            scroll.getHorizontalScrollBar().setValue(scroll.getHorizontalScrollBar().getMaximum());
            assertThat(scroll.getViewport().getViewPosition().x + scroll.getViewport().getExtentSize().width)
                    .isGreaterThanOrEqualTo(panel.table.getWidth());
        });
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container nested) layout(nested);
    }

    private static void assertControlsVisible(Container root, Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton || child instanceof JTextField || child instanceof JComboBox<?>) {
                for (Container parent = child.getParent(); parent != null; parent = parent.getParent()) {
                    Rectangle bounds = SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), parent);
                    assertThat(new Rectangle(0, 0, parent.getWidth(), parent.getHeight()).contains(bounds))
                            .as("%s clipped in %s: %s", child.getClass().getSimpleName(), parent.getClass().getSimpleName(), bounds)
                            .isTrue();
                    if (parent == root) break;
                }
            } else if (child instanceof Container nested && !(child instanceof JTable) && !(child instanceof JScrollPane)) {
                assertControlsVisible(root, nested);
            }
        }
    }
}

