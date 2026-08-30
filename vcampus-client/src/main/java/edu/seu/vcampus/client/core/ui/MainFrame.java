package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.navigation.PageNavigator;
import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.shell.ApplicationStatusBar;
import edu.seu.vcampus.client.core.ui.shell.IdentityHeader;
import edu.seu.vcampus.client.core.ui.shell.ModulePlaceholderPage;
import edu.seu.vcampus.client.core.ui.shell.PermissionNavigation;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.client.library.ui.LibraryWorkspacePanel;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.Set;

/** Shared application shell containing identity, navigation, content, and status seams. */
public final class MainFrame extends JFrame {
    private final JPanel header;
    private final JPanel navigation;
    private final JPanel content = new JPanel();
    private final JPanel footer;
    private final PageNavigator pageNavigator = new PageNavigator(content);

    /** Creates the structural shell for compatibility with existing layout tests. */
    public MainFrame() {
        this(null, null);
    }

    /** Creates the shell with a signed-in identity and no connection binding. */
    public MainFrame(UserView user) {
        this(user, null);
    }

    /** Creates the complete demo shell with identity and live connection status. */
    public MainFrame(UserView user, ClientConnection connection) {
        this(user, connection, null, Set.of());
    }

    /** Creates the user shell with the real permission-filtered library workspace. */
    public MainFrame(UserView user, ClientConnection connection,
            LibraryClientService library, Set<String> permissions) {
        super("vCampus");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        header = new IdentityHeader(user, connection);
        footer = new ApplicationStatusBar();
        navigation = new PermissionNavigation(pageNavigator::show);
        content.setBackground(UiColors.BACKGROUND_PAGE);
        registerPages(library, permissions);
        add(header, BorderLayout.NORTH);
        add(navigation, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        setSize(UiDimensions.MAIN_WINDOW);
        setMinimumSize(UiDimensions.MAIN_MINIMUM);
        setLocationRelativeTo(null);
    }

    private void registerPages(LibraryClientService library, Set<String> permissions) {
        register("student", "学籍档案", "用于查看和维护校园身份与学籍信息。");
        register("course", "课程中心", "用于课程查询、选课和学习安排。");
        if (library == null) register("library", "图书借阅", "用于检索馆藏并管理个人借阅。");
        else pageNavigator.register("library", new LibraryWorkspacePanel(library, permissions));
        register("shop", "校园商城", "用于浏览校园商品和管理订单。");
        register("account", "账户设置", "用于查看账户信息和安全设置。");
        pageNavigator.show("student");
    }

    private void register(String id, String title, String description) {
        pageNavigator.register(id, new ModulePlaceholderPage(title, description));
    }

    /** Returns the shared identity header. */
    public JPanel header() { return header; }

    /** Returns the fixed top-level navigation. */
    public JPanel navigation() { return navigation; }

    /** Returns the card-layout page content region. */
    public JPanel content() { return content; }

    /** Returns the shared application status bar. */
    public JPanel footer() { return footer; }

    /** Returns the shared card-layout page navigator. */
    public PageNavigator pageNavigator() { return pageNavigator; }
}
