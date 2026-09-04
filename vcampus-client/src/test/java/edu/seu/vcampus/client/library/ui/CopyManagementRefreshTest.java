package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CopyManagementRefreshTest {
    @Test
    void successfulAddRefreshesWorkspaceEvenAfterDialogCloses() throws Exception {
        LibraryClientService service = mock(LibraryClientService.class);
        CompletableFuture<BookCopyView> added = new CompletableFuture<>();
        when(service.addCopy(any())).thenReturn(added);
        AtomicInteger refreshes = new AtomicInteger();
        CopyManagementPanel panel = new CopyManagementPanel(service);
        panel.setAfterMutation(refreshes::incrementAndGet);
        SwingUtilities.invokeAndWait(() -> {
            panel.add(new AddBookCopyCommand("book-1", "BC-1", "A-01"));
            panel.removeNotify();
            added.complete(new BookCopyView("copy-1", "book-1", "BC-1", "A-01", CopyStatus.AVAILABLE, 0));
        });
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(refreshes.get()).isEqualTo(1);
    }
}
