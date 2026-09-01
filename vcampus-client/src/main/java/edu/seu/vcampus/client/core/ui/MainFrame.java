package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.navigation.PageNavigator;
import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.course.ui.CourseUiComposition;
import edu.seu.vcampus.client.core.ui.shell.ApplicationStatusBar;
import edu.seu.vcampus.client.core.ui.shell.IdentityHeader;
import edu.seu.vcampus.client.core.ui.shell.ModulePlaceholderPage;
import edu.seu.vcampus.client.core.ui.shell.PermissionNavigation;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;
import java.util.Objects;
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
        this(null, null);
    }

    /** Creates the shell with a signed-in identity and no connection binding. */
    public MainFrame(UserView user) {
        this(user, null);
    }

    /** Creates the complete demo shell with identity and live connection status. */
    public MainFrame(UserView user, ClientConnection connection) {
        super("vCampus");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        header = new IdentityHeader(user, connection);
        footer = new ApplicationStatusBar();
        navigation = new PermissionNavigation(pageNavigator::show);
        content.setBackground(UiColors.BACKGROUND_PAGE);
        registerPlaceholders();
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

    /** Creates a role-restricted course shell and supplies the one login-return handoff. */
    public MainFrame(UserView user, CourseClientService courses, ClientConnection connection,
                     Runnable onAuthenticationFailure) {
        this(user, connection);
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(courses, "courses");
        Objects.requireNonNull(connection, "connection");
        Runnable handoff = Objects.requireNonNull(onAuthenticationFailure, "onAuthenticationFailure");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addIdentity(header, user);
        removeAuthenticationFailureListener = courses.addAuthenticationFailureListener(failure -> {
            if (!authenticationHandoffStarted.compareAndSet(false, true)) return;
            connection.setSessionToken(null);
            SwingUtilities.invokeLater(() -> {
                dispose();
                handoff.run();
            });
        });
        installCoursePages(new CourseUiComposition(courses).pagesFor(user.role()));
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (!authenticationHandoffStarted.get()) connection.close();
            }

            @Override public void windowClosed(WindowEvent event) {
                removeAuthenticationFailureListener.run();
            }
        });
    }

    private void registerPlaceholders() {
        register("student", "学籍档案", "用于查看和维护校园身份与学籍信息。");
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
        addIdentity(header, user);
        JPanel home = new JPanel(new GridLayout(0, 1, 8, 8));
        for (String module : new String[]{"学籍", "选课", "图书馆", "商城"}) {
            home.add(new JLabel(module + "：建设中"));
        }
        pageNavigator.register("home", home);
    }

    private static void addIdentity(JPanel header, UserView user) {
        header.add(new JLabel("当前用户：" + user.loginId() + "（" + user.role().name() + "）"),
                BorderLayout.CENTER);
    }

    private void installCoursePages(Map<String, JPanel> pages) {
        navigation.removeAll();
        navigation.setLayout(new GridLayout(0, 1, 8, 8));
        String firstPage = null;
        for (Map.Entry<String, JPanel> entry : pages.entrySet()) {
            String pageId = entry.getKey();
            pageNavigator.register(pageId, entry.getValue());
            JButton button = new JButton(navigationLabel(pageId));
            button.addActionListener(event -> pageNavigator.show(pageId));
            navigation.add(button);
            if (firstPage == null) firstPage = pageId;
        }
        navigation.revalidate();
        navigation.repaint();
        if (firstPage != null) pageNavigator.show(firstPage);
    }

    private static String navigationLabel(String pageId) {
        return switch (pageId) {
            case "course.offerings" -> "教学班查询";
            case "course.enrollments" -> "我的选课";
            case "course.schedule" -> "我的课表";
            case "course.adjustment" -> "退改补";
            case "course.retake" -> "重修";
            case "course.terms" -> "学期管理";
            case "course.catalog" -> "课程目录";
            case "course.offering-admin" -> "教学班管理";
            case "course.outcome-import" -> "修读结果导入";
            case "course.adjustment-audit" -> "退改补审计";
            default -> throw new IllegalArgumentException("Unknown course page: " + pageId);
        };
    }

    /** Returns the shared identity header. */
    public JPanel header() { return header; }

    /** Returns the fixed top-level navigation. */
    public JPanel navigation() { return navigation; }

    /** Returns the card-layout page content region. */
    public JPanel content() { return content; }

    /** Replaces one of the fixed top-level module pages. */
    public void installPage(String pageId, JComponent page) {
        pageNavigator.replace(pageId, page);
    }

    /** Returns the shared application status bar. */
    public JPanel footer() { return footer; }

    /** Returns the shared card-layout page navigator. */
    public PageNavigator pageNavigator() { return pageNavigator; }
}
