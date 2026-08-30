package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.Set;

/** Permission-filtered workspace containing every library page. */
public final class LibraryWorkspacePanel extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private boolean initialRefreshScheduled;

    public LibraryWorkspacePanel(LibraryClientService service, Set<String> permissions) {
        super(new BorderLayout());
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(permissions, "permissions");
        setName("page.library");
        setBackground(LibraryPalette.PAGE);
        tabs.setName("library.tabs");
        BookSearchPanel search = new BookSearchPanel(service);
        BookDetailPanel detail = new BookDetailPanel(service);
        search.connectDetail(detail);
        JSplitPane catalog = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, search, detail);
        catalog.setResizeWeight(0.58); catalog.setDividerLocation(0.58);
        catalog.setDividerSize(8); catalog.setBorder(BorderFactory.createEmptyBorder());
        catalog.setBackground(LibraryPalette.PAGE);
        JPanel catalogPage = new JPanel(new BorderLayout());
        catalogPage.setBackground(LibraryPalette.PAGE);
        catalogPage.setName("library.catalog"); catalogPage.add(catalog);
        addTab("馆藏检索", catalogPage, search::search);
        CurrentLoansPanel currentLoans = new CurrentLoansPanel(service);
        LoanHistoryPanel history = new LoanHistoryPanel(service);
        addTab("当前借阅", currentLoans, currentLoans::refresh);
        addTab("借阅历史", history, history::refresh);
        if (permissions.contains("LIBRARY_ADMIN")) {
            BookManagementPanel books = new BookManagementPanel(service);
            LoanAdminPanel loans = new LoanAdminPanel(service);
            addTab("书目管理", books, books::refresh);
            CopyManagementPanel copies = new CopyManagementPanel(service);
            addTab("副本管理", copies, copies::loadCopies);
            addTab("借阅管理", loans, loans::refresh);
            addTab("设置", new LibraryPolicyPanel(service), null);
        }
        tabs.addChangeListener(event -> refreshSelected());
        add(tabs, BorderLayout.CENTER);
        LibraryUiStyle.styleTabs(tabs);
        LibraryUiStyle.apply(this);
    }

    private void addTab(String title, JComponent component, Runnable refresh) {
        component.putClientProperty("library.refresh", refresh);
        tabs.addTab(title, component);
    }

    private void refreshSelected() {
        Component selected = tabs.getSelectedComponent();
        if (selected instanceof JComponent component) {
            Object refresh = component.getClientProperty("library.refresh");
            if (refresh instanceof Runnable action) action.run();
        }
    }

    @Override public void addNotify() {
        super.addNotify();
        if (!initialRefreshScheduled) {
            initialRefreshScheduled = true;
            SwingUtilities.invokeLater(this::refreshSelected);
        }
    }
}
