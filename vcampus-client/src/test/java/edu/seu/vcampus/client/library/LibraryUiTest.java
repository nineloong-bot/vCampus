package edu.seu.vcampus.client.library;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.client.library.service.LibraryRequestException;
import edu.seu.vcampus.client.library.ui.LibraryWorkspacePanel;
import edu.seu.vcampus.client.library.ui.CurrentLoansPanel;
import edu.seu.vcampus.client.library.ui.LoanHistoryPanel;
import edu.seu.vcampus.client.library.ui.LoanAdminPanel;
import edu.seu.vcampus.client.library.ui.LibraryPolicyPanel;
import edu.seu.vcampus.client.library.ui.BookManagementPanel;
import edu.seu.vcampus.client.library.ui.BookSearchPanel;
import edu.seu.vcampus.client.library.ui.BookDetailPanel;
import edu.seu.vcampus.client.library.ui.CopyManagementPanel;
import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.common.user.*;
import org.junit.jupiter.api.Test;

import javax.swing.JTabbedPane;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.any;
import edu.seu.vcampus.common.library.BookSummary;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.client.core.network.ConnectionState;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.library.LoanStatus;
import java.time.Instant;
import edu.seu.vcampus.common.library.LibraryPolicyView;
import edu.seu.vcampus.common.library.UpdateLibraryPolicyCommand;
import edu.seu.vcampus.common.library.*;
import java.time.LocalDate;

class LibraryUiTest {
    private final LibraryClientService service = mock(LibraryClientService.class);

    @Test
    void signedInReadersReceiveAllFourPersonalLibraryPages() {
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(service, Set.of());

        assertThat(tabTitles(workspace)).containsExactly("馆藏检索", "当前借阅", "借阅历史");
        assertThat(named(workspace, "library.book-search")).isNotNull();
        assertThat(named(workspace, "library.book-detail")).isNotNull();
        assertThat(named(workspace, "library.loan-action")).isNotNull();
    }

    @Test
    void libraryAdministratorsReceiveManagementPagesWithoutPersonalBorrowingControls() {
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(
                service, Set.of("LIBRARY_ADMIN"));

        assertThat(tabTitles(workspace)).containsExactly("馆藏检索", "书目管理", "副本管理",
                "借阅管理", "设置");
        assertThat(named(workspace, "library.loan-action")).isNull();
        assertThat(named(workspace, "library.book-management")).isNotNull();
        assertThat(named(workspace, "library.copy-management")).isNotNull();
        assertThat(named(workspace, "library.loan-admin")).isNotNull();
        assertThat(named(workspace, "library.policy")).isNotNull();
    }

    @Test
    void userMainFrameHostsTheRealPermissionFilteredLibraryWorkspace() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 30, 12, 0);
        UserView user = new UserView("user-1", "ADMIN", UserRole.ADMIN,
                AccountStatus.ACTIVE, false, now, 0, now, now);
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        MainFrame frame = new MainFrame(user, connection, service,
                Set.of("LIBRARY_ADMIN"));

        assertThat(named(frame.content(), "page.library"))
                .isInstanceOf(LibraryWorkspacePanel.class);
        assertThat(tabTitles((Container) named(frame.content(), "page.library")))
                .contains("书目管理", "设置");
        frame.dispose();
    }

    @Test
    void administratorRoleHidesPersonalBorrowingEvenWithoutLibraryPermission() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 30, 12, 0);
        UserView user = new UserView("user-1", "ADMIN", UserRole.ADMIN,
                AccountStatus.ACTIVE, false, now, 0, now, now);
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        MainFrame frame = new MainFrame(user, connection, service, Set.of());
        Container workspace = (Container) named(frame.content(), "page.library");

        assertThat(tabTitles(workspace)).containsExactly("馆藏检索");
        assertThat(named(workspace, "library.loan-action")).isNull();
        frame.dispose();
    }

    @Test
    void catalogSearchRendersTheLatestAsyncResult() throws Exception {
        when(service.searchBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(new BookSummary("book-1", "978", "Java 核心技术",
                        "Cay Horstmann", "计算机", 2, 3)), 0, 20, 1)));
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(service, Set.of());

        ((JButton) button(workspace, "查询馆藏")).doClick();
        SwingUtilities.invokeAndWait(() -> { });

        JTable table = first(workspace, JTable.class);
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("Java 核心技术");
        assertThat(labels(workspace)).contains("共 1 条");
        verify(service).searchBooks(new BookSearchQuery("", null, false, 1, 20));
    }

    @Test
    void unavailableCopyProducesAnExplicitBorrowFailureWarning() {
        BookCopyView copy = new BookCopyView("copy-1", "book-1", "LIB-0001", "A-01",
                CopyStatus.BORROWED, 0);
        BookDetailPanel panel = new BookDetailPanel(service);
        panel.showBook(new BookDetail("book-1", "978", "Java 核心技术", "作者", "出版社",
                LocalDate.of(2026, 1, 1), "计算机", "", true, 0, List.of(copy)));
        first(panel, JTable.class).setRowSelectionInterval(0, 0);

        panel.borrowSelected();

        assertThat(labels(panel)).contains("借阅失败：该副本当前不可借，请选择可借副本");
    }

    @Test
    void hoveringALibraryTableCellShowsItsCompleteValue() {
        String barcode = "LIBRARY-COPY-BARCODE-WITH-A-VERY-LONG-FULL-NAME-0001";
        BookDetailPanel panel = new BookDetailPanel(service);
        panel.showBook(new BookDetail("book-1", "978", "Java 核心技术", "作者", "出版社",
                LocalDate.of(2026, 1, 1), "计算机", "", true, 0,
                List.of(new BookCopyView("copy-1", "book-1", barcode, "A-01",
                        CopyStatus.AVAILABLE, 0))));
        JTable table = first(panel, JTable.class);
        Rectangle cell = table.getCellRect(0, 0, true);
        MouseEvent hover = new MouseEvent(table, MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, cell.x + 1, cell.y + 1, 0, false);

        assertThat(table.getToolTipText(hover)).isEqualTo(barcode);
    }

    @Test
    void currentLoansRefreshRendersServerDataWithoutBlockingTheEdt() throws Exception {
        LoanView loan = new LoanView("loan-1", "copy-1", "book-1", "user-1",
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                null, 1, LoanStatus.ACTIVE, 0, "AS812", "Java 核心技术", "LIB-0001");
        when(service.getCurrentLoans()).thenReturn(
                CompletableFuture.completedFuture(List.of(loan)));
        CurrentLoansPanel panel = new CurrentLoansPanel(service);

        panel.refresh();
        SwingUtilities.invokeAndWait(() -> { });

        JTable table = first(panel, JTable.class);
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(columnNames(table)).containsExactly("借阅号", "书名", "馆藏条码", "借出时间",
                "到期时间", "续借次数", "状态");
        assertThat(table.getValueAt(0, 1)).isEqualTo("Java 核心技术");
        assertThat(table.getValueAt(0, 2)).isEqualTo("LIB-0001");
        assertThat(table.getValueAt(0, 5)).isEqualTo(1);
        assertThat(table.getValueAt(0, 6)).isEqualTo("借阅中");
        assertThat(labels(panel)).contains("共 1 条当前借阅");
    }

    @Test
    void historyAndAdministrativeLoanPagesRenderTheirQueries() throws Exception {
        LoanView loan = new LoanView("8ca302ec-5781-43d3-9d4d-1ee3db135432",
                "copy-2", "book-2", "user-2",
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-07-20T00:00:00Z"), 0, LoanStatus.RETURNED, 1,
                "AS812", "Java 核心技术", "LIB-0001");
        PageResult<LoanView> page = new PageResult<>(List.of(loan), 0, 20, 1);
        when(service.getLoanHistory(any())).thenReturn(CompletableFuture.completedFuture(page));
        when(service.searchAllLoans(any())).thenReturn(CompletableFuture.completedFuture(page));
        LoanHistoryPanel history = new LoanHistoryPanel(service);
        LoanAdminPanel admin = new LoanAdminPanel(service);

        history.refresh(); admin.refresh();
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(first(admin, JTable.class).getValueAt(0, 0)).isEqualTo("BR-8CA302EC");
        assertThat(first(admin, JTable.class).getValueAt(0, 1)).isEqualTo("AS812");
        assertThat(first(admin, JTable.class).getValueAt(0, 2))
                .isEqualTo("Java 核心技术 / LIB-0001");
        assertThat(columnNames(first(history, JTable.class))).containsExactly("借阅号", "书名", "馆藏条码",
                "借出时间", "到期时间", "归还时间", "续借次数", "状态");
        assertThat(first(history, JTable.class).getValueAt(0, 1)).isEqualTo("Java 核心技术");
        assertThat(first(history, JTable.class).getValueAt(0, 7)).isEqualTo("已归还");
    }

    @Test
    void administratorCanResolveTheSelectedBorrowersLoan() throws Exception {
        LoanView loan = new LoanView("loan-9", "copy-9", "book-9", "user-9",
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                null, 0, LoanStatus.ACTIVE, 4, "AS812", "Java 核心技术", "LIB-0009");
        when(service.searchAllLoans(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(loan), 1, 20, 1)));
        when(service.resolveLoan(any())).thenReturn(CompletableFuture.completedFuture(loan));
        LoanAdminPanel panel = new LoanAdminPanel(service);
        panel.refresh(); SwingUtilities.invokeAndWait(() -> { });
        first(panel, JTable.class).setRowSelectionInterval(0, 0);

        panel.returnSelected();
        SwingUtilities.invokeAndWait(() -> { });

        verify(service).resolveLoan(new AdminResolveLoanCommand("loan-9", LoanStatus.RETURNED, 4));
        assertThat(button(panel, "办理归还")).isNotNull();
        assertThat(button(panel, "标记遗失")).isNotNull();
    }

    @Test
    void managementTablesUseSearchControlsAndNoMeaninglessActionColumn() {
        BookManagementPanel books = new BookManagementPanel(service);
        CopyManagementPanel copies = new CopyManagementPanel(service);
        LoanAdminPanel loans = new LoanAdminPanel(service);

        assertThat(button(books, "搜索书目")).isNotNull();
        assertThat(button(copies, "搜索副本")).isNotNull();
        assertThat(button(loans, "查询账号")).isNotNull();
        assertThat(columnNames(first(books, JTable.class))).doesNotContain("操作");
        assertThat(columnNames(first(copies, JTable.class))).doesNotContain("操作");
    }

    @Test
    void openingCopyManagementLoadsAllCopies() throws Exception {
        BookSummary summary = new BookSummary("book-1", "978", "Java 核心技术", "作者", "计算机", 1, 1);
        BookCopyView copy = new BookCopyView("copy-1", "book-1", "LIB-0001", "A-01", CopyStatus.AVAILABLE, 0);
        when(service.searchManagedBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(summary), 1, 100, 1)));
        when(service.getBook("book-1")).thenReturn(CompletableFuture.completedFuture(
                new BookDetail("book-1", "978", "Java 核心技术", "作者", "出版社",
                        LocalDate.of(2026, 1, 1), "计算机", "", true, 0, List.of(copy))));
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(service, Set.of("LIBRARY_ADMIN"));
        JTabbedPane tabs = (JTabbedPane) named(workspace, "library.tabs");

        tabs.setSelectedIndex(2);
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        JTable table = first((Container) tabs.getSelectedComponent(), JTable.class);
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("LIB-0001");
    }

    @Test
    void successfulCopyStateChangeRefreshesDisplayedStateAndCachedVersion() throws Exception {
        BookSummary summary = new BookSummary("book-1", "978", "Java 核心技术",
                "作者", "计算机", 0, 1, true);
        BookCopyView lost = new BookCopyView("copy-1", "book-1", "LIB-0001", "A-01",
                CopyStatus.LOST, 2);
        BookCopyView found = new BookCopyView("copy-1", "book-1", "LIB-0001", "A-01",
                CopyStatus.AVAILABLE, 3);
        when(service.searchManagedBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(summary), 1, 100, 1)));
        when(service.getBook("book-1")).thenReturn(CompletableFuture.completedFuture(
                new BookDetail("book-1", "978", "Java 核心技术", "作者", "出版社",
                        LocalDate.of(2026, 1, 1), "计算机", "", true, 0, List.of(lost))));
        ChangeCopyStatusCommand command = new ChangeCopyStatusCommand(
                "copy-1", CopyStatus.AVAILABLE, 2);
        when(service.changeCopyStatus(command)).thenReturn(CompletableFuture.completedFuture(found));
        CopyManagementPanel panel = new CopyManagementPanel(service);

        panel.loadCopies(); SwingUtilities.invokeAndWait(() -> { });
        panel.changeStatus(command); SwingUtilities.invokeAndWait(() -> { });

        assertThat(first(panel, JTable.class).getValueAt(0, 3)).isEqualTo("可借");
        assertThat(labels(panel)).contains("副本状态已更新");
    }

    @Test
    void selectedCatalogBookClearsLoadingMessageAfterDetailArrives() throws Exception {
        BookSummary summary = new BookSummary("book-1", "978", "Java 核心技术", "作者", "计算机", 1, 1);
        when(service.searchBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(summary), 1, 20, 1)));
        when(service.getBook("book-1")).thenReturn(CompletableFuture.completedFuture(
                new BookDetail("book-1", "978", "Java 核心技术", "作者", "出版社",
                        LocalDate.of(2026, 1, 1), "计算机", "", true, 0, List.of())));
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(service, Set.of());
        BookSearchPanel search = (BookSearchPanel) named(workspace, "library.book-search");
        search.search(); SwingUtilities.invokeAndWait(() -> { });
        first(search, JTable.class).setRowSelectionInterval(0, 0);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(labels(search)).contains("图书详情已加载").doesNotContain("正在加载图书详情");
    }

    @Test
    void policyPageShowsSuccessfulSaveResult() throws Exception {
        UpdateLibraryPolicyCommand command = new UpdateLibraryPolicyCommand(
                "STUDENT", 5, 30, 1, 15, 0);
        when(service.updatePolicy(command)).thenReturn(CompletableFuture.completedFuture(
                new LibraryPolicyView("STUDENT", 5, 30, 1, 15, 1)));
        LibraryPolicyPanel panel = new LibraryPolicyPanel(service);

        panel.save(command);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(labels(panel)).contains("学生借阅策略已保存");
    }

    @Test
    void settingsUsesFixedStudentTeacherRowsAndShowsLiveSystemStatus() throws Exception {
        when(service.searchBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 1, 1, 0)));
        when(service.getPolicies()).thenReturn(CompletableFuture.completedFuture(List.of(
                new LibraryPolicyView("STUDENT", 7, 35, 2, 18, 4),
                new LibraryPolicyView("TEACHER", 12, 70, 3, 32, 7))));
        UpdateLibraryPolicyCommand saved = new UpdateLibraryPolicyCommand(
                "STUDENT", 7, 35, 2, 18, 4);
        when(service.updatePolicy(saved)).thenReturn(CompletableFuture.completedFuture(
                new LibraryPolicyView("STUDENT", 7, 35, 2, 18, 5)));
        LibraryPolicyPanel panel = new LibraryPolicyPanel(service);

        panel.refreshStatus();
        SwingUtilities.invokeAndWait(() -> { });
        ((JButton) button(panel, "保存学生设置")).doClick();
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(first(panel, JTable.class)).isNull();
        assertThat(labels(panel)).contains("学生", "教师", "服务端状态", "数据库状态", "已连接", "可访问");
        verify(service).updatePolicy(saved);
    }

    @Test
    void currentLoanActionsUseTheSelectedLoanVersion() throws Exception {
        LoanView loan = new LoanView("loan-1", "copy-1", "book-1", "user-1",
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                null, 0, LoanStatus.ACTIVE, 3);
        when(service.getCurrentLoans()).thenReturn(CompletableFuture.completedFuture(List.of(loan)));
        when(service.renew(any())).thenReturn(CompletableFuture.completedFuture(loan));
        when(service.returnBook(any())).thenReturn(CompletableFuture.completedFuture(loan));
        CurrentLoansPanel panel = new CurrentLoansPanel(service);
        panel.refresh(); SwingUtilities.invokeAndWait(() -> { });
        first(panel, JTable.class).setRowSelectionInterval(0, 0);

        panel.renewSelected(); panel.returnSelected();
        SwingUtilities.invokeAndWait(() -> { });

        verify(service).renew(new RenewLoanCommand("loan-1", 3));
        verify(service).returnBook(new ReturnBookCommand("loan-1", 3));
    }

    @Test
    void managementPagesSubmitBookAndCopyCommands() throws Exception {
        CreateBookCommand book = new CreateBookCommand("978", "Java", "作者", "出版社",
                LocalDate.of(2026, 1, 1), "计算机", "简介");
        AddBookCopyCommand copy = new AddBookCopyCommand("book-1", "BC-1", "A-01");
        ChangeCopyStatusCommand status = new ChangeCopyStatusCommand("copy-1", CopyStatus.DAMAGED, 2);
        when(service.createBook(book)).thenReturn(CompletableFuture.completedFuture(
                new BookView("book-1", "978", "Java", "作者", "出版社", LocalDate.of(2026, 1, 1),
                        "计算机", "简介", true, 0)));
        when(service.addCopy(copy)).thenReturn(CompletableFuture.completedFuture(
                new BookCopyView("copy-1", "book-1", "BC-1", "A-01", CopyStatus.AVAILABLE, 0)));
        when(service.changeCopyStatus(status)).thenReturn(CompletableFuture.completedFuture(
                new BookCopyView("copy-1", "book-1", "BC-1", "A-01", CopyStatus.DAMAGED, 3)));
        BookManagementPanel books = new BookManagementPanel(service);
        CopyManagementPanel copies = new CopyManagementPanel(service);

        books.create(book); copies.add(copy); copies.changeStatus(status);
        SwingUtilities.invokeAndWait(() -> { });

        verify(service).createBook(book);
        verify(service).addCopy(copy);
        verify(service).changeCopyStatus(status);
        assertThat(labels(books)).contains("书目已新增");
        assertThat(labels(copies)).contains("副本状态已更新");
    }

    @Test
    void selectingAQueryTabAutomaticallyRefreshesItsData() throws Exception {
        when(service.searchBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 1, 20, 0)));
        when(service.searchManagedBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 1, 100, 0)));
        when(service.searchAllLoans(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 1, 20, 0)));
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(service, Set.of("LIBRARY_ADMIN"));
        JTabbedPane tabs = (JTabbedPane) named(workspace, "library.tabs");

        tabs.setSelectedIndex(1);
        tabs.setSelectedIndex(2);
        tabs.setSelectedIndex(3);
        SwingUtilities.invokeAndWait(() -> { });

        verify(service, atLeastOnce()).searchManagedBooks(
                new BookSearchQuery("", null, false, 1, 100));
        verify(service).searchAllLoans(new AdminLoanSearchQuery(null, null, 1, 20));
    }

    @Test
    void unifiedRefreshButtonReloadsTheCurrentlySelectedLibraryPage() throws Exception {
        when(service.searchBooks(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 1, 20, 0)));
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(service, Set.of());

        JButton refresh = (JButton) button(workspace, "刷新当前页");
        assertThat(refresh).isNotNull();
        refresh.doClick();
        SwingUtilities.invokeAndWait(() -> { });

        verify(service).searchBooks(new BookSearchQuery("", null, false, 1, 20));
    }

    @Test
    void catalogSearchOffersEveryBookFieldAndAnAnyFieldOption() {
        BookSearchPanel panel = new BookSearchPanel(service);

        JComboBox<?> field = (JComboBox<?>) named(panel, "library.book-search-field");

        assertThat(field).isNotNull();
        assertThat(java.util.stream.IntStream.range(0, field.getItemCount())
                .mapToObj(index -> String.valueOf(field.getItemAt(index))).toList())
                .containsExactly("全部栏目", "书名", "作者", "ISBN", "分类", "出版社");
    }

    @Test
    void bookCopyStatusesAreDisplayedInConsistentChinese() {
        BookDetailPanel panel = new BookDetailPanel(service);
        panel.showBook(new BookDetail("book-1", "978", "Java 核心技术", "作者", "出版社",
                LocalDate.of(2026, 1, 1), "计算机", "", true, 0,
                java.util.Arrays.stream(CopyStatus.values())
                        .map(status -> new BookCopyView(status.name(), "book-1", status.name(),
                                "A-01", status, 0))
                        .toList()));

        JTable table = first(panel, JTable.class);
        assertThat(java.util.stream.IntStream.range(0, table.getRowCount())
                .mapToObj(row -> table.getValueAt(row, 2)).toList())
                .containsExactly("可借", "已借出", "已遗失", "已损坏");
    }

    @Test
    void unifiedRefreshButtonSharesTheTabStripInsteadOfAddingAnotherRow() {
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(service, Set.of());
        workspace.setSize(1000, 700);
        workspace.doLayout();
        JTabbedPane tabs = (JTabbedPane) named(workspace, "library.tabs");
        JButton refresh = (JButton) button(workspace, "刷新当前页");
        tabs.getParent().doLayout();
        refresh.getParent().doLayout();

        Rectangle refreshBounds = SwingUtilities.convertRectangle(
                refresh.getParent(), refresh.getBounds(), workspace);

        Rectangle lastTab = tabs.getBoundsAt(tabs.getTabCount() - 1);
        Rectangle lastTabBounds = SwingUtilities.convertRectangle(tabs, lastTab, workspace);
        assertThat(refreshBounds.y).isEqualTo(lastTabBounds.y);
        assertThat(refreshBounds.x).isGreaterThanOrEqualTo(lastTabBounds.x + lastTabBounds.width);
        assertThat(refreshBounds.y + refreshBounds.height)
                .isLessThanOrEqualTo(lastTabBounds.y + lastTabBounds.height);
    }

    @Test
    void concurrentUpdateFailureShowsActionableRefreshMessage() throws Exception {
        LoanView loan = new LoanView("loan-3", "copy-3", "book-3", "user-1",
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                null, 0, LoanStatus.ACTIVE, 2);
        when(service.getCurrentLoans()).thenReturn(CompletableFuture.completedFuture(List.of(loan)));
        when(service.renew(any())).thenReturn(CompletableFuture.failedFuture(
                new LibraryRequestException("COMMON_CONCURRENT_MODIFICATION", "conflict")));
        CurrentLoansPanel panel = new CurrentLoansPanel(service);
        panel.refresh(); SwingUtilities.invokeAndWait(() -> { });
        first(panel, JTable.class).setRowSelectionInterval(0, 0);

        panel.renewSelected();
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(labels(panel)).contains("数据已被其他操作修改，请刷新后重试。");
    }

    @Test
    void libraryTablesUseModernSurfaceSelectionAndLightweightGrid() {
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(service, Set.of());
        JTable table = first(workspace, JTable.class);

        assertThat(table.getBackground()).isEqualTo(java.awt.Color.decode("#FFFFFF"));
        assertThat(table.getSelectionBackground()).isEqualTo(UiColors.BACKGROUND_SUBTLE);
        assertThat(table.getTableHeader().getBackground()).isEqualTo(UiColors.BACKGROUND_SUBTLE);
        assertThat(((JButton) button(workspace, "查询馆藏")).getBackground())
                .isEqualTo(UiColors.PRIMARY);
        assertThat(table.getShowHorizontalLines()).isTrue();
        assertThat(table.getShowVerticalLines()).isFalse();
    }

    private static Component button(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container nested) {
                Component match = button(nested, text); if (match != null) return match;
            }
        }
        return null;
    }

    private static <T extends Component> T first(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T match = first(nested, type); if (match != null) return match;
            }
        }
        return null;
    }

    private static String labels(Container root) {
        StringBuilder text = new StringBuilder();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) text.append(label.getText()).append(' ');
            if (child instanceof Container nested) text.append(labels(nested));
        }
        return text.toString();
    }

    private static java.util.List<String> tabTitles(Container root) {
        JTabbedPane tabs = (JTabbedPane) named(root, "library.tabs");
        return java.util.stream.IntStream.range(0, tabs.getTabCount())
                .mapToObj(tabs::getTitleAt).toList();
    }

    private static java.util.List<String> columnNames(JTable table) {
        return java.util.stream.IntStream.range(0, table.getColumnCount())
                .mapToObj(table::getColumnName).toList();
    }

    private static Component named(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) {
                Component match = named(nested, name);
                if (match != null) return match;
            }
        }
        return null;
    }
}
