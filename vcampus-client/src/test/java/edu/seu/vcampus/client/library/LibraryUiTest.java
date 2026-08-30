package edu.seu.vcampus.client.library;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.client.library.service.LibraryRequestException;
import edu.seu.vcampus.client.library.ui.LibraryWorkspacePanel;
import edu.seu.vcampus.client.library.ui.CurrentLoansPanel;
import edu.seu.vcampus.client.library.ui.LoanHistoryPanel;
import edu.seu.vcampus.client.library.ui.LoanAdminPanel;
import edu.seu.vcampus.client.library.ui.LibraryPolicyPanel;
import edu.seu.vcampus.client.library.ui.BookManagementPanel;
import edu.seu.vcampus.client.library.ui.CopyManagementPanel;
import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.common.user.*;
import org.junit.jupiter.api.Test;

import javax.swing.JTabbedPane;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
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
    void libraryAdministratorsReceiveTheFourManagementPages() {
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(
                service, Set.of("LIBRARY_ADMIN"));

        assertThat(tabTitles(workspace)).containsExactly("馆藏检索", "当前借阅", "借阅历史",
                "书目管理", "副本管理", "借阅管理", "借阅策略");
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
                .contains("书目管理", "借阅策略");
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
    void currentLoansRefreshRendersServerDataWithoutBlockingTheEdt() throws Exception {
        LoanView loan = new LoanView("loan-1", "copy-1", "book-1", "user-1",
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                null, 0, LoanStatus.ACTIVE, 0);
        when(service.getCurrentLoans()).thenReturn(
                CompletableFuture.completedFuture(List.of(loan)));
        CurrentLoansPanel panel = new CurrentLoansPanel(service);

        panel.refresh();
        SwingUtilities.invokeAndWait(() -> { });

        JTable table = first(panel, JTable.class);
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("loan-1");
        assertThat(labels(panel)).contains("共 1 条当前借阅");
    }

    @Test
    void historyAndAdministrativeLoanPagesRenderTheirQueries() throws Exception {
        LoanView loan = new LoanView("loan-2", "copy-2", "book-2", "user-2",
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-07-20T00:00:00Z"), 0, LoanStatus.RETURNED, 1);
        PageResult<LoanView> page = new PageResult<>(List.of(loan), 0, 20, 1);
        when(service.getLoanHistory(any())).thenReturn(CompletableFuture.completedFuture(page));
        when(service.searchAllLoans(any())).thenReturn(CompletableFuture.completedFuture(page));
        LoanHistoryPanel history = new LoanHistoryPanel(service);
        LoanAdminPanel admin = new LoanAdminPanel(service);

        history.refresh(); admin.refresh();
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(first(history, JTable.class).getValueAt(0, 0)).isEqualTo("loan-2");
        assertThat(first(admin, JTable.class).getValueAt(0, 1)).isEqualTo("user-2");
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
        when(service.getCurrentLoans()).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(service.getLoanHistory(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 1, 20, 0)));
        when(service.searchAllLoans(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 1, 20, 0)));
        LibraryWorkspacePanel workspace = new LibraryWorkspacePanel(service, Set.of("LIBRARY_ADMIN"));
        JTabbedPane tabs = (JTabbedPane) named(workspace, "library.tabs");

        tabs.setSelectedIndex(1);
        tabs.setSelectedIndex(2);
        tabs.setSelectedIndex(3);
        tabs.setSelectedIndex(5);
        SwingUtilities.invokeAndWait(() -> { });

        verify(service).getCurrentLoans();
        verify(service).getLoanHistory(new LoanHistoryQuery(null, 1, 20));
        verify(service, atLeastOnce()).searchBooks(new BookSearchQuery("", null, false, 1, 100));
        verify(service).searchAllLoans(new AdminLoanSearchQuery(null, null, 1, 20));
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
