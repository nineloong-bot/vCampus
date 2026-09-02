package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.user.UserRole;
import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.Set;

/** Permission-filtered workspace containing every library page. */
public final class LibraryWorkspacePanel extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private boolean initialRefreshScheduled;

    public LibraryWorkspacePanel(LibraryClientService service, Set<String> permissions) {
        this(service, permissions,
                permissions.contains("LIBRARY_ADMIN") ? UserRole.ADMIN : UserRole.STUDENT);
    }

    public LibraryWorkspacePanel(LibraryClientService service, Set<String> permissions,
            UserRole role) {
        super(new BorderLayout());
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(role, "role");
        setName("page.library");
        setBackground(LibraryPalette.PAGE);
        tabs.setName("library.tabs");
        boolean administrator = role == UserRole.ADMIN;
        boolean mayManageLibrary = permissions.contains("LIBRARY_ADMIN");
        BookSearchPanel search = new BookSearchPanel(service);
        BookDetailPanel detail = new BookDetailPanel(service, !administrator);
        search.connectDetail(detail);
        JSplitPane catalog = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, search, detail);
        catalog.setResizeWeight(0.58); catalog.setDividerLocation(0.58);
        catalog.setDividerSize(8); catalog.setBorder(BorderFactory.createEmptyBorder());
        catalog.setBackground(LibraryPalette.PAGE);
        JPanel catalogPage = new JPanel(new BorderLayout());
        catalogPage.setBackground(LibraryPalette.PAGE);
        catalogPage.setName("library.catalog"); catalogPage.add(catalog);
        addTab("馆藏检索", catalogPage, search::search);
        if (!administrator) {
            CurrentLoansPanel currentLoans = new CurrentLoansPanel(service);
            LoanHistoryPanel history = new LoanHistoryPanel(service);
            addTab("当前借阅", currentLoans, currentLoans::refresh);
            addTab("借阅历史", history, history::refresh);
        }
        if (mayManageLibrary) {
            BookManagementPanel books = new BookManagementPanel(service);
            LoanAdminPanel loans = new LoanAdminPanel(service);
            addTab("书目管理", books, books::refresh);
            CopyManagementPanel copies = new CopyManagementPanel(service);
            addTab("副本管理", copies, copies::loadCopies);
            addTab("借阅管理", loans, loans::refresh);
            LibraryPolicyPanel settings = new LibraryPolicyPanel(service);
            addTab("设置", settings, settings::refreshStatus);
        }
        tabs.addChangeListener(event -> refreshSelected());
        JButton refresh = new JButton("刷新当前页");
        refresh.setName("library.refresh-current");
        refresh.setMargin(new Insets(0, 10, 0, 10));
        refresh.addActionListener(event -> refreshSelected());
        JLayeredPane tabArea = new JLayeredPane() {
            @Override public void doLayout() {
                tabs.setBounds(0, 0, getWidth(), getHeight());
                tabs.doLayout();
                Dimension size = refresh.getPreferredSize();
                Rectangle lastTab = tabs.getBoundsAt(tabs.getTabCount() - 1);
                int x = Math.max(lastTab.x + lastTab.width + 8,
                        getWidth() - size.width - 12);
                int width = Math.max(0, Math.min(size.width, getWidth() - x - 12));
                refresh.setBounds(x, lastTab.y, width, lastTab.height);
            }
        };
        tabArea.add(tabs, JLayeredPane.DEFAULT_LAYER);
        tabArea.add(refresh, JLayeredPane.PALETTE_LAYER);
        add(tabArea, BorderLayout.CENTER);
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
