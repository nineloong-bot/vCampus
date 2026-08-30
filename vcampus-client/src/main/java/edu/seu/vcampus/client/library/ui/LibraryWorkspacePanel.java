package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.Set;

/** Permission-filtered workspace containing every library page. */
public final class LibraryWorkspacePanel extends JPanel {
    public LibraryWorkspacePanel(LibraryClientService service, Set<String> permissions) {
        super(new BorderLayout());
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(permissions, "permissions");
        setName("page.library");
        JTabbedPane tabs = new JTabbedPane(); tabs.setName("library.tabs");
        BookSearchPanel search = new BookSearchPanel(service);
        BookDetailPanel detail = new BookDetailPanel(service);
        search.connectDetail(detail);
        JSplitPane catalog = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, search, detail);
        catalog.setResizeWeight(0.6); catalog.setDividerLocation(0.6);
        JPanel catalogPage = new JPanel(new BorderLayout());
        catalogPage.setName("library.catalog"); catalogPage.add(catalog);
        tabs.addTab("馆藏检索", catalogPage);
        tabs.addTab("当前借阅", new CurrentLoansPanel(service));
        tabs.addTab("借阅历史", new LoanHistoryPanel(service));
        if (permissions.contains("LIBRARY_ADMIN")) {
            tabs.addTab("书目管理", new BookManagementPanel(service));
            tabs.addTab("副本管理", new CopyManagementPanel(service));
            tabs.addTab("借阅管理", new LoanAdminPanel(service));
            tabs.addTab("借阅策略", new LibraryPolicyPanel(service));
        }
        add(tabs, BorderLayout.CENTER);
    }
}
