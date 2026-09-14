package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import edu.seu.vcampus.common.paging.PageResult;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class LibraryPaginationTest {
    @Test void managedBooksCanPageBothWaysAndSearchResetsPage() throws Exception {
        var service = mock(LibraryClientService.class);
        var queries = new ArrayList<BookSearchQuery>();
        when(service.searchManagedBooks(any())).thenAnswer(call -> {
            BookSearchQuery q = call.getArgument(0); queries.add(q);
            var book = new BookSummary("book-" + q.page(), "isbn", "第" + q.page() + "页图书", "作者", "分类", 1, 1);
            return CompletableFuture.completedFuture(new PageResult<>(List.of(book), q.page(), q.pageSize(), 101));
        });
        var panel = new BookManagementPanel(service);
        SwingUtilities.invokeAndWait(panel::refresh); flush();
        assertThat(button(panel, "下一页")).isNotNull();
        SwingUtilities.invokeAndWait(() -> button(panel, "下一页").doClick()); flush();
        assertThat(panel.table.getValueAt(0, 1)).isEqualTo("第2页图书");
        SwingUtilities.invokeAndWait(() -> button(panel, "上一页").doClick()); flush();
        assertThat(panel.table.getValueAt(0, 1)).isEqualTo("第1页图书");
        SwingUtilities.invokeAndWait(() -> button(panel, "下一页").doClick()); flush();
        SwingUtilities.invokeAndWait(() -> {
            first(panel, JTextField.class).setText("Java");
            button(panel, "搜索书目").doClick();
        }); flush();
        assertThat(queries).extracting(BookSearchQuery::page).containsExactly(1, 2, 1, 2, 1);
        assertThat(queries.getLast().keyword()).isEqualTo("Java");
        assertThat(button(panel, "上一页").isEnabled()).isFalse();
    }

    @Test void changingCatalogPageDiscardsLateDetailsFromThePreviousPage() throws Exception {
        var service = mock(LibraryClientService.class);
        when(service.searchBooks(any())).thenAnswer(call -> {
            BookSearchQuery q = call.getArgument(0);
            var book = new BookSummary("book-" + q.page(), "isbn", "图书", "作者", "分类", 1, 1);
            return CompletableFuture.completedFuture(new PageResult<>(List.of(book), q.page(), 20, 21));
        });
        var pendingDetail = new CompletableFuture<BookDetail>();
        when(service.getBook("book-1")).thenReturn(pendingDetail);
        var search = new BookSearchPanel(service); var detail = new BookDetailPanel(service);
        search.connectDetail(detail);
        SwingUtilities.invokeAndWait(search::search); flush();
        SwingUtilities.invokeAndWait(() -> search.table.setRowSelectionInterval(0, 0));
        SwingUtilities.invokeAndWait(() -> button(search, "下一页").doClick()); flush();
        pendingDetail.complete(new BookDetail("book-1", "isbn", "旧图书", "作者", "出版社",
                java.time.LocalDate.of(2026, 1, 1), "分类", "", true, 0,
                List.of(new BookCopyView("copy-1", "book-1", "BARCODE-1", "A-01", CopyStatus.AVAILABLE, 0))));
        flush();
        assertThat(detail.table.getRowCount()).isZero();
        assertThat(button(search, "上一页").isEnabled()).isTrue();
        assertThat(button(search, "查询馆藏").isEnabled()).isTrue();
    }

    @Test void disappearingLastPageFallsBackToAnExistingPage() throws Exception {
        var service = mock(LibraryClientService.class);
        var queries = new ArrayList<BookSearchQuery>();
        when(service.searchBooks(any())).thenAnswer(call -> {
            BookSearchQuery q = call.getArgument(0); queries.add(q);
            if (q.page() == 2) return CompletableFuture.completedFuture(new PageResult<>(List.of(), 2, 20, 20));
            var book = new BookSummary("book-1", "isbn", "图书", "作者", "分类", 1, 1);
            return CompletableFuture.completedFuture(new PageResult<>(List.of(book), 1, 20, queries.size() == 1 ? 21 : 20));
        });
        var panel = new BookSearchPanel(service);
        SwingUtilities.invokeAndWait(panel::search); flush();
        SwingUtilities.invokeAndWait(() -> button(panel, "下一页").doClick()); flush(); flush();
        assertThat(queries).extracting(BookSearchQuery::page).containsExactly(1, 2, 1);
        assertThat(panel.table.getRowCount()).isEqualTo(1);
        assertThat(button(panel, "下一页").isEnabled()).isFalse();
    }

    @Test void catalogCanReachSecondPageAndNewKeywordStartsAtFirstPage() throws Exception {
        var service = mock(LibraryClientService.class);
        var queries = new ArrayList<BookSearchQuery>();
        when(service.searchBooks(any())).thenAnswer(call -> {
            BookSearchQuery q = call.getArgument(0); queries.add(q);
            var book = new BookSummary("book-" + q.page(), "isbn", "第" + q.page() + "页图书", "作者", "分类", 1, 1);
            return CompletableFuture.completedFuture(new PageResult<>(List.of(book), q.page(), 20, 21));
        });
        var panel = new BookSearchPanel(service);
        SwingUtilities.invokeAndWait(panel::search); flush();
        assertThat(button(panel, "下一页")).isNotNull();
        SwingUtilities.invokeAndWait(() -> button(panel, "下一页").doClick()); flush();
        assertThat(panel.table.getValueAt(0, 0)).isEqualTo("第2页图书");
        assertThat(button(panel, "下一页").isEnabled()).isFalse();
        SwingUtilities.invokeAndWait(() -> {
            first(panel, JTextField.class).setText("Java");
            button(panel, "查询馆藏").doClick();
        }); flush();
        assertThat(queries).extracting(BookSearchQuery::page).containsExactly(1, 2, 1);
        assertThat(queries.getLast().keyword()).isEqualTo("Java");
        assertThat(button(panel, "上一页").isEnabled()).isFalse();
    }

    @Test void failedAdminPageLoadKeepsCurrentPageAndCanRetry() throws Exception {
        var service = mock(LibraryClientService.class);
        var queries = new ArrayList<AdminLoanSearchQuery>();
        var pending = new CompletableFuture<PageResult<LoanView>>();
        var now = Instant.parse("2026-09-01T00:00:00Z");
        when(service.searchAllLoans(any())).thenAnswer(call -> {
            AdminLoanSearchQuery q = call.getArgument(0); queries.add(q);
            if (queries.size() == 2) return pending;
            var loan = new LoanView("loan-" + q.page(), "copy", "book", "user", now, now, null, 0, LoanStatus.ACTIVE, 0);
            return CompletableFuture.completedFuture(new PageResult<>(List.of(loan), q.page(), 20, 21));
        });
        var panel = new LoanAdminPanel(service);
        SwingUtilities.invokeAndWait(panel::refresh); flush();
        assertThat(button(panel, "下一页")).isNotNull();
        SwingUtilities.invokeAndWait(() -> button(panel, "下一页").doClick());
        assertThat(button(panel, "下一页").isEnabled()).isFalse();
        pending.completeExceptionally(new IllegalArgumentException("test failure")); flush();
        assertThat(panel.table.getValueAt(0, 0)).isEqualTo("BR-LOAN1");
        assertThat(button(panel, "下一页")).isNotNull();
        SwingUtilities.invokeAndWait(() -> button(panel, "下一页").doClick()); flush();
        assertThat(panel.table.getValueAt(0, 0)).isEqualTo("BR-LOAN2");
        assertThat(queries).extracting(AdminLoanSearchQuery::page).containsExactly(1, 2, 2);
        SwingUtilities.invokeAndWait(() -> {
            first(panel, JTextField.class).setText("STUDENT01");
            button(panel, "查询账号").doClick();
        }); flush();
        assertThat(queries.getLast().page()).isEqualTo(1);
        assertThat(queries.getLast().borrowerUserId()).isEqualTo("STUDENT01");
    }

    private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }
    private static JButton button(Container panel, String text) {
        for (Component child : panel.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container nested) { JButton found = button(nested, text); if (found != null) return found; }
        }
        return null;
    }
    private static <T> T first(Container panel, Class<T> type) {
        for (Component child : panel.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) { T found = first(nested, type); if (found != null) return found; }
        }
        return null;
    }
}

