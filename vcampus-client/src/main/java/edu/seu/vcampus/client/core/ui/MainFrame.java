package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.navigation.PageNavigator;
import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.course.ui.CourseUiComposition;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.client.library.ui.LibraryWorkspacePanel;
import edu.seu.vcampus.client.shop.service.ShopClientService;
import edu.seu.vcampus.client.shop.ui.ShopUiInstaller;
import edu.seu.vcampus.client.shop.ui.style.SharedShopUiKitAdapter;
import edu.seu.vcampus.client.core.ui.shell.ApplicationStatusBar;
import edu.seu.vcampus.client.core.ui.shell.IdentityHeader;
import edu.seu.vcampus.client.core.ui.shell.ModulePlaceholderPage;
import edu.seu.vcampus.client.core.ui.shell.PermissionNavigation;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.ui.StudentModulePageFactory;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared application shell containing identity, navigation, content, and status seams. */
public final class MainFrame extends JFrame {
    private final JPanel header;
    private final JPanel navigation;
    private final JPanel content = new JPanel();
    private final JPanel footer;
    private final PageNavigator pageNavigator = new PageNavigator(content);
    private final AtomicBoolean authenticationHandoffStarted = new AtomicBoolean();
    private Runnable removeAuthenticationFailureListener = () -> { };

    /** Creates the structural shell for compatibility with existing layout tests. */
    public MainFrame() {
        this(null, null, (StudentClientService) null);
    }

    /** Creates the shell with a signed-in identity and no connection binding. */
    public MainFrame(UserView user) {
        this(user, null, (StudentClientService) null);
    }

    /** Creates the complete demo shell with identity and live connection status. */
    public MainFrame(UserView user, ClientConnection connection) {
        this(user, connection, null);
    }

    /** Creates the complete shell with an optional live student self-service page. */
    public MainFrame(UserView user, ClientConnection connection,
                     StudentClientService students) {
        super("vCampus");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        header = new IdentityHeader(user, connection);
        footer = new ApplicationStatusBar();
        navigation = new PermissionNavigation(pageNavigator::show);
        content.setBackground(UiColors.BACKGROUND_PAGE);
        registerPages(user, connection, students);
        add(header, BorderLayout.NORTH);
        add(navigation, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        setSize(UiDimensions.MAIN_WINDOW);
        setMinimumSize(UiDimensions.MAIN_MINIMUM);
        setLocationRelativeTo(null);
    }

    /** Creates a role-restricted course shell using the shared authenticated connection. */
    public MainFrame(UserView user, CourseClientService courses, ClientConnection connection) {
        this(user, courses, connection, () -> { });
    }

    /** Creates a course shell and supplies the one login-return handoff. */
    public MainFrame(UserView user, CourseClientService courses, ClientConnection connection,
                     Runnable onAuthenticationFailure) {
        this(user, connection, (StudentClientService) null);
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(courses, "courses");
        Objects.requireNonNull(connection, "connection");
        Runnable handoff = Objects.requireNonNull(onAuthenticationFailure, "onAuthenticationFailure");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        removeAuthenticationFailureListener = courses.addAuthenticationFailureListener(failure -> {
            if (!authenticationHandoffStarted.compareAndSet(false, true)) return;
            connection.setSessionToken(null);
            SwingUtilities.invokeLater(() -> {
                dispose();
                handoff.run();
            });
        });
        installPage("course", new CourseUiComposition(courses).workspaceFor(user.role()));
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (!authenticationHandoffStarted.get()) connection.close();
            }

            @Override public void windowClosed(WindowEvent event) {
                removeAuthenticationFailureListener.run();
            }
        });
    }

    /** Creates the user shell with the permission-filtered library workspace. */
    public MainFrame(UserView user, ClientConnection connection,
                     LibraryClientService library, Set<String> permissions) {
        this(user, connection, (StudentClientService) null);
        Objects.requireNonNull(library, "library");
        installPage("library", new LibraryWorkspacePanel(library,
                Objects.requireNonNull(permissions, "permissions"), user.role()));
    }

    /** Creates the production authenticated shell with every campus module installed. */
    public MainFrame(UserView user, ClientConnection connection,
                     StudentClientService students, CourseClientService courses,
                     LibraryClientService library, ShopClientService shop,
                     UserClientService users, Set<String> permissions,
                     Runnable onAuthenticationFailure) {
        this(user, connection, students);
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(onAuthenticationFailure, "onAuthenticationFailure");
        installPage("course", new CourseUiComposition(
                Objects.requireNonNull(courses, "courses"),
                Objects.requireNonNull(users, "users")).workspaceFor(user.role()));
        installPage("library", new LibraryWorkspacePanel(
                Objects.requireNonNull(library, "library"),
                Objects.requireNonNull(permissions, "permissions"), user.role()));
        ShopUiInstaller.install(this, user, Objects.requireNonNull(shop, "shop"),
                new SharedShopUiKitAdapter(), onAuthenticationFailure);
    }

    private void registerPages(UserView user, ClientConnection connection,
                               StudentClientService students) {
        pageNavigator.register("student", StudentModulePageFactory.create(user, students, connection));
        register("course", "课程中心", "用于课程查询、选课和学习安排。");
        register("library", "图书借阅", "用于检索馆藏并管理个人借阅。");
        register("shop", "校园商城", "用于浏览校园商品和管理订单。");
        register("account", "账户设置", "用于查看账户信息和安全设置。");
        pageNavigator.show("student");
    }

    private void register(String id, String title, String description) {
        pageNavigator.register(id, new ModulePlaceholderPage(title, description));
    }

    static void configureLoggedInContent(JPanel header, PageNavigator pageNavigator, UserView user) {
        header.add(new JLabel("当前用户：" + user.loginId() + "（" + user.role().name() + "）"),
                BorderLayout.CENTER);
        JPanel home = new JPanel(new GridLayout(0, 1, 8, 8));
        for (String module : new String[]{"学籍", "选课", "图书馆", "商城"}) {
            home.add(new JLabel(module + "：建设中"));
        }
        pageNavigator.register("home", home);
    }

    /** Replaces one of the fixed top-level module pages. */
    public void installPage(String pageId, JComponent page) {
        pageNavigator.replace(pageId, page);
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

    /** Returns the registered top-level page identifiers. */
    public Set<String> registeredPageIds() { return pageNavigator.pageIds(); }
}
