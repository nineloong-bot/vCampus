package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import edu.seu.vcampus.common.paging.PageResult;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LibraryEditorWorkspaceTest {
    @Test void bookAndCopyEditorsAreHiddenUntilRequested() throws Exception {
        LibraryClientService service = mock(LibraryClientService.class);
        BookSummary book = new BookSummary("book", "978", "Java", "作者", "计算机", 1, 1);
        SwingUtilities.invokeAndWait(() -> {
            BookManagementPanel books = new BookManagementPanel(service);
            CopyManagementPanel copies = new CopyManagementPanel(service, book);
            EmbeddedEditorHost bookHost = host(books);
            EmbeddedEditorHost copyHost = host(copies);
            assertThat(bookHost.isEditorOpen()).isFalse();
            assertThat(copyHost.isEditorOpen()).isFalse();
            button(books, "新增书目").doClick();
            button(copies, "新增副本").doClick();
            assertThat(bookHost.isEditorOpen()).isTrue();
            assertThat(copyHost.isEditorOpen()).isTrue();
        });
    }

    @Test void loanResolutionUsesAnEmbeddedWorkspace() throws Exception {
        LibraryClientService service = mock(LibraryClientService.class);
        LoanView loan = new LoanView("loan", "copy", "book", "user", Instant.now(),
                Instant.now().plusSeconds(3600), null, 0, LoanStatus.ACTIVE, 1,
                "STUDENT", "Java", "BC-1");
        when(service.searchAllLoans(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(new ArrayList<>(List.of(loan)), 1, 20, 1)));
        LoanAdminPanel panel = new LoanAdminPanel(service);
        panel.refresh();
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            JTable table = component(panel, JTable.class);
            table.setRowSelectionInterval(0, 0);
            EmbeddedEditorHost host = host(panel);
            assertThat(host.isEditorOpen()).isFalse();
            button(panel, "办理归还").doClick();
            assertThat(host.isEditorOpen()).isTrue();
        });
    }

    private static EmbeddedEditorHost host(Container root) {
        return component(root, EmbeddedEditorHost.class);
    }

    private static JButton button(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container nested) {
                JButton found = button(nested, text); if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends Component> T component(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = component(nested, type); if (found != null) return found;
            }
        }
        return null;
    }
}
