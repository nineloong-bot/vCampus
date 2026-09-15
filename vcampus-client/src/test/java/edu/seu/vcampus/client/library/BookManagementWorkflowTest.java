package edu.seu.vcampus.client.library;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.client.library.ui.BookManagementPanel;
import edu.seu.vcampus.client.library.ui.CopyManagementPanel;
import edu.seu.vcampus.client.library.ui.LibraryWorkspacePanel;
import edu.seu.vcampus.client.library.ui.BookSearchPanel;
import edu.seu.vcampus.client.library.ui.BookDetailPanel;
import edu.seu.vcampus.client.library.ui.CurrentLoansPanel;
import edu.seu.vcampus.common.library.*;
import edu.seu.vcampus.common.paging.PageResult;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookManagementWorkflowTest {
    private final LibraryClientService service = mock(LibraryClientService.class);
    private final BookSummary book = new BookSummary("book-1", "9787300000001", "Java", "Author", "CS", 0, 0);
    private final BookCopyView copy = new BookCopyView("copy-1", "book-1", "BC-2", "A-02", CopyStatus.AVAILABLE, 0);

    @Test
    void addWorkspaceKeepsItsBookWhenSelectionChangesBeforeSubmit() throws Exception {
        BookSummary other = new BookSummary("book-2", "9782", "数据库", "作者", "CS", 0, 0);
        when(service.getBook(any())).thenReturn(CompletableFuture.completedFuture(
                new BookDetail("book-1", book.isbn(), "Java", "Author", "Press", LocalDate.of(2026, 1, 1),
                        "CS", "", true, 0, List.of())));
        CompletableFuture<BookCopyView> added = new CompletableFuture<>();
        when(service.addCopy(any())).thenReturn(added);
        CopyManagementPanel panel = new CopyManagementPanel(service, book);
        SwingUtilities.invokeAndWait(() -> {
            button(panel, "新增副本").doClick();
            text(panel, "library.copy-editor.barcode").setText("BC-2");
            text(panel, "library.copy-editor.location").setText("A-02");
            panel.selectBook(other);
            button(panel, "确认新增").doClick();
        });
        verify(service).addCopy(new AddBookCopyCommand("book-1", "BC-2", "A-02"));
    }

    @Test
    void failedMutationStillLoadsNewlySelectedBooksCopies() throws Exception {
        BookSummary other = new BookSummary("book-2", "9782", "数据库", "作者", "CS", 1, 1);
        when(service.getBook("book-2")).thenReturn(CompletableFuture.completedFuture(
                new BookDetail("book-2", "9782", "数据库", "作者", "出版社", LocalDate.of(2026, 1, 1),
                        "CS", "", true, 0, List.of(new BookCopyView("copy-2", "book-2", "DB-002", "B-02", CopyStatus.AVAILABLE, 0)))));
        CompletableFuture<BookCopyView> added = new CompletableFuture<>();
        when(service.addCopy(any())).thenReturn(added);
        CopyManagementPanel panel = new CopyManagementPanel(service, book);
        SwingUtilities.invokeAndWait(() -> {
            panel.add(new AddBookCopyCommand("book-1", "BC-2", "A-02"));
            panel.selectBook(other);
            added.completeExceptionally(new IllegalArgumentException("duplicate barcode"));
        });
        SwingUtilities.invokeAndWait(() -> { }); SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            assertThat(first(panel, JTable.class).getRowCount()).isEqualTo(1);
            assertThat(first(panel, JTable.class).getValueAt(0, 0)).isEqualTo("DB-002");
        });
    }

    @Test
    void changingSelectedBookIgnoresLateCopyResponseAndRefreshKeepsSelection() throws Exception {
        BookSummary other = new BookSummary("book-2", "9782", "数据库", "作者", "CS", 1, 1);
        when(service.searchManagedBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(book, other), 1, 100, 2)));
        CompletableFuture<BookDetail> late = new CompletableFuture<>();
        when(service.getBook("book-1")).thenReturn(late);
        when(service.getBook("book-2")).thenReturn(CompletableFuture.completedFuture(
                new BookDetail("book-2", "9782", "数据库", "作者", "出版社", LocalDate.of(2026, 1, 1),
                        "CS", "", true, 0, List.of(new BookCopyView("copy-2", "book-2", "DB-002", "B-02", CopyStatus.AVAILABLE, 0)))));
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(service, java.util.Set.of("LIBRARY_ADMIN"));
        BookManagementPanel books = first(workspace, BookManagementPanel.class);
        SwingUtilities.invokeAndWait(books::refresh); SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> first(books, JTable.class).setRowSelectionInterval(1, 1));
        SwingUtilities.invokeAndWait(() -> { });
        late.complete(new BookDetail("book-1", book.isbn(), "Java", "作者", "出版社", LocalDate.of(2026, 1, 1),
                "CS", "", true, 0, List.of(copy)));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> assertThat(first(first(workspace, CopyManagementPanel.class), JTable.class)
                .getValueAt(0, 0)).isEqualTo("DB-002"));
        SwingUtilities.invokeAndWait(books::refresh); SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            assertThat(first(books, JTable.class).getSelectedRow()).isEqualTo(1);
            assertThat(first(first(workspace, CopyManagementPanel.class), JTable.class).getValueAt(0, 0)).isEqualTo("DB-002");
        });
    }

    @Test
    void borrowingRefreshesCatalogSelectedDetailAndCurrentLoans() throws Exception {
        when(service.searchBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(book), 1, 20, 1)));
        BookDetail before = new BookDetail("book-1", book.isbn(), "Java", "Author", "Press", LocalDate.of(2026, 1, 1),
                "CS", "", true, 0, List.of(copy));
        BookDetail after = new BookDetail("book-1", book.isbn(), "Java", "Author", "Press", LocalDate.of(2026, 1, 1),
                "CS", "", true, 0, List.of(new BookCopyView("copy-1", "book-1", "BC-2", "A-02", CopyStatus.BORROWED, 1)));
        when(service.getBook("book-1")).thenReturn(CompletableFuture.completedFuture(before), CompletableFuture.completedFuture(after));
        LoanView loan = new LoanView("loan-1", "copy-1", "book-1", "user-1", java.time.Instant.parse("2026-09-04T00:00:00Z"),
                java.time.Instant.parse("2026-10-04T00:00:00Z"), null, 0, LoanStatus.ACTIVE, 0);
        when(service.borrow(any())).thenReturn(CompletableFuture.completedFuture(loan));
        when(service.getCurrentLoans()).thenReturn(CompletableFuture.completedFuture(List.of(loan)));
        when(service.getLoanHistory(any())).thenReturn(CompletableFuture.completedFuture(new PageResult<>(List.of(), 1, 20, 0)));
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(service, java.util.Set.of());
        BookSearchPanel search = first(workspace, BookSearchPanel.class);
        BookDetailPanel detail = first(workspace, BookDetailPanel.class);
        SwingUtilities.invokeAndWait(search::search);
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> first(search, JTable.class).setRowSelectionInterval(0, 0));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            first(detail, JTable.class).setRowSelectionInterval(0, 0);
            detail.borrowSelected();
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            assertThat(first(search, JTable.class).getSelectedRow()).isEqualTo(0);
            assertThat(first(detail, JTable.class).getValueAt(0, 2)).isEqualTo("已借出");
            assertThat(first(first(workspace, CurrentLoansPanel.class), JTable.class).getRowCount()).isEqualTo(1);
        });
    }

    @Test
    void creatingBookRefreshesEveryLibraryPage() throws Exception {
        when(service.searchBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(book), 1, 20, 1)));
        when(service.searchManagedBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(book), 1, 100, 1)));
        when(service.getBook("book-1")).thenReturn(CompletableFuture.completedFuture(
                new BookDetail("book-1", book.isbn(), "Java", "Author", "Press", LocalDate.of(2026, 1, 1),
                        "CS", "", true, 0, List.of(copy))));
        when(service.searchAllLoans(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 1, 20, 0)));
        when(service.getPolicies()).thenReturn(CompletableFuture.completedFuture(List.of()));
        CompletableFuture<BookView> created = new CompletableFuture<>();
        when(service.createBook(any())).thenReturn(created);
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(service, java.util.Set.of("LIBRARY_ADMIN"));
        SwingUtilities.invokeAndWait(() -> first(workspace, BookManagementPanel.class).create(
                new CreateBookCommand(book.isbn(), "Java", "Author", "Press", LocalDate.of(2026, 1, 1), "CS", "", "A-02")));
        SwingUtilities.invokeAndWait(() -> {
            first(workspace, BookManagementPanel.class).refresh();
            created.complete(new BookView("book-1", book.isbn(), "Java", "Author", "Press", LocalDate.of(2026, 1, 1),
                    "CS", "", true, 0));
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            assertThat(first(first(workspace, BookManagementPanel.class), JTable.class).getRowCount()).isEqualTo(1);
            assertThat(first(first(workspace, CopyManagementPanel.class), JTable.class).getRowCount()).isEqualTo(1);
        });
        verify(service).searchAllLoans(any());
        verify(service).getPolicies();
        verify(service, atLeastOnce()).searchBooks(any());
    }

    @Test
    void refreshingPoliciesPreservesUnsavedEditsToOtherRole() throws Exception {
        when(service.searchBooks(any())).thenReturn(CompletableFuture.completedFuture(new PageResult<>(List.of(), 1, 1, 0)));
        when(service.getPolicies()).thenReturn(CompletableFuture.completedFuture(List.of(
                new LibraryPolicyView("STUDENT", 5, 30, 1, 15, 0),
                new LibraryPolicyView("TEACHER", 10, 60, 2, 30, 0))));
        var panel = new edu.seu.vcampus.client.library.ui.LibraryPolicyPanel(service);
        SwingUtilities.invokeAndWait(panel::refreshStatus);
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            first(panel, JSpinner.class).setValue(7);
            panel.refreshAfterMutation();
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> assertThat(first(panel, JSpinner.class).getValue()).isEqualTo(7));
    }

    @Test
    void addingWhileInitialLoadIsPendingStillDisplaysAllCopies() throws Exception {
        CompletableFuture<BookDetail> initial = new CompletableFuture<>();
        CompletableFuture<BookCopyView> added = new CompletableFuture<>();
        BookCopyView existing = new BookCopyView("copy-old", "book-1", "BC-1", "A-01", CopyStatus.AVAILABLE, 0);
        BookDetail before = new BookDetail("book-1", book.isbn(), "Java", "Author", "Press", LocalDate.of(2026, 1, 1),
                "CS", "", true, 0, List.of(existing));
        BookDetail after = new BookDetail("book-1", book.isbn(), "Java", "Author", "Press", LocalDate.of(2026, 1, 1),
                "CS", "", true, 0, List.of(existing, copy));
        when(service.getBook("book-1")).thenReturn(initial, CompletableFuture.completedFuture(after));
        when(service.addCopy(any())).thenReturn(added);
        CopyManagementPanel panel = new CopyManagementPanel(service, book);
        SwingUtilities.invokeAndWait(() -> {
            panel.loadCopies();
            panel.add(new AddBookCopyCommand("book-1", "BC-2", "A-02"));
            assertThat(button(panel, "新增副本").isEnabled()).isFalse();
            panel.loadCopies();
            initial.complete(before);
            added.complete(copy);
        });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            JTable table = first(panel, JTable.class);
            assertThat(table.getRowCount()).isEqualTo(2);
            assertThat(table.getValueAt(0, 0)).isEqualTo("BC-1");
            assertThat(table.getValueAt(1, 0)).isEqualTo("BC-2");
            assertThat(button(panel, "新增副本").isEnabled()).isTrue();
        });
    }

    @Test
    void createFormSendsFirstCopyLocationAndCustomBarcode() throws Exception {
        when(service.createBook(any())).thenReturn(CompletableFuture.completedFuture(
                new BookView("book-1", book.isbn(), "Java", "Author", "Press", LocalDate.of(2026, 1, 1),
                        "CS", "", true, 0)));
        SwingUtilities.invokeAndWait(() -> {
            BookManagementPanel panel = new BookManagementPanel(service);
            button(panel, "新增书目").doClick();
            text(panel, "library.book-form.isbn").setText(book.isbn());
            text(panel, "library.book-form.title").setText("Java");
            text(panel, "library.book-form.author").setText("Author");
            text(panel, "library.book-form.publisher").setText("Press");
            text(panel, "library.book-form.publish-date").setText("2026-01-01");
            text(panel, "library.book-form.category").setText("CS");
            text(panel, "library.book-form.location").setText(" A-02 ");
            text(panel, "library.book-form.barcode").setText(" LIB-CUSTOM-01 ");
            button(panel, "保存书目").doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });
        verify(service).createBook(new CreateBookCommand("9787300000001", "Java", "Author", "Press",
                LocalDate.of(2026, 1, 1), "CS", "", "A-02", "LIB-CUSTOM-01"));
    }

    @Test
    void partialIsbnMatchDoesNotAddCopyToAnotherBook() throws Exception {
        when(service.searchManagedBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(book), 1, 100, 1)));
        SwingUtilities.invokeAndWait(() -> {
            CopyManagementPanel panel = new CopyManagementPanel(service);
            button(panel, "新增副本").doClick();
            text(panel, "library.copy-editor.isbn").setText("978730");
            text(panel, "library.copy-editor.barcode").setText("BC-2");
            text(panel, "library.copy-editor.location").setText("A-02");
            button(panel, "确认新增").doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });
        verify(service, never()).addCopy(any());
    }

    @Test
    void isbnEntryResolvesInternalBookIdBeforeAddingCopy() throws Exception {
        when(service.searchManagedBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(book), 1, 100, 1)));
        when(service.addCopy(any())).thenReturn(CompletableFuture.completedFuture(copy));
        SwingUtilities.invokeAndWait(() -> {
            CopyManagementPanel panel = new CopyManagementPanel(service);
            button(panel, "新增副本").doClick();
            text(panel, "library.copy-editor.isbn").setText(" 9787300000001 ");
            text(panel, "library.copy-editor.barcode").setText("BC-2");
            text(panel, "library.copy-editor.location").setText("A-02");
            button(panel, "确认新增").doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });
        verify(service).addCopy(new AddBookCopyCommand("book-1", "BC-2", "A-02"));
        verify(service).searchManagedBooks(new BookSearchQuery("9787300000001", BookSearchField.ISBN, null, false, 1, 100));
    }

    @Test
    void selectedBookAddsCopyInRightPaneWithoutTypingIsbn() throws Exception {
        when(service.searchManagedBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(book), 1, 100, 1)));
        when(service.getBook("book-1")).thenReturn(CompletableFuture.completedFuture(
                new BookDetail("book-1", book.isbn(), "Java", "Author", "Press", LocalDate.of(2026, 1, 1),
                        "CS", "", true, 0, List.of())));
        when(service.addCopy(any())).thenReturn(CompletableFuture.completedFuture(copy));
        BookManagementPanel panel = new BookManagementPanel(service);
        CopyManagementPanel copies = new CopyManagementPanel(service);
        panel.connectCopies(copies);
        SwingUtilities.invokeAndWait(panel::refresh);
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            button(copies, "新增副本").doClick();
            text(copies, "library.copy-editor.barcode").setText("BC-2");
            text(copies, "library.copy-editor.location").setText("A-02");
            button(copies, "确认新增").doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });
        verify(service).addCopy(new AddBookCopyCommand("book-1", "BC-2", "A-02"));
    }

    private static JTextField text(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTextField field && name.equals(field.getName())) return field;
            if (child instanceof Container nested) {
                JTextField found = text(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton button(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container nested) {
                JButton found = button(nested, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends Component> T first(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = first(nested, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
