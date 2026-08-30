package edu.seu.vcampus.client.core.ui;

import edu.seu.vcampus.client.core.navigation.PageNavigator;
import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.course.ui.CourseUiComposition;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Minimal layout-managed application shell extended by feature UI plans. */
public final class MainFrame extends JFrame {
    private final JPanel header = new JPanel(new BorderLayout());
    private final JPanel navigation = new JPanel();
    private final JPanel content = new JPanel();
    private final JPanel footer = new JPanel(new BorderLayout());
    private final PageNavigator pageNavigator = new PageNavigator(content);
    private final AtomicBoolean authenticationHandoffStarted = new AtomicBoolean();
    private Runnable removeAuthenticationFailureListener = () -> { };

    /** Creates the structural header, navigation, content, and footer seams. */
    public MainFrame() {
        this(null);
    }

    /** Creates the application shell with the logged-in demo identity. */
    public MainFrame(UserView user) {
        super("vCampus");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        header.add(new JLabel("vCampus"), BorderLayout.WEST);
        header.add(new ConnectionStatusPanel(), BorderLayout.EAST);
        footer.add(new JLabel("就绪"), BorderLayout.WEST);
        add(header, BorderLayout.NORTH);
        add(navigation, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        if (user != null) {
            configureLoggedInContent(header, pageNavigator, user);
        }
        pack();
    }

    /** Creates a role-restricted course shell using the shared authenticated connection. */
    public MainFrame(UserView user, CourseClientService courses, ClientConnection connection) {
        this(user, courses, connection, () -> { });
    }

    /** Creates a role-restricted course shell and supplies the one login-return handoff. */
    public MainFrame(UserView user, CourseClientService courses, ClientConnection connection,
            Runnable onAuthenticationFailure) {
        this();
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
        setMinimumSize(new Dimension(920, 620));
        pack();
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

    /** Returns the header extension point. */
    public JPanel header() {
        return header;
    }

    /** Returns the navigation extension point. */
    public JPanel navigation() {
        return navigation;
    }

    /** Returns the page content extension point. */
    public JPanel content() {
        return content;
    }

    /** Returns the footer extension point. */
    public JPanel footer() {
        return footer;
    }

    /** Returns the shared card-layout page navigator. */
    public PageNavigator pageNavigator() {
        return pageNavigator;
    }
}
