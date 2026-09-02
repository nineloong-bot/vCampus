package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.course.ui.CourseUiComposition;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.client.shop.service.ShopClientService;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.user.LoginResult;

import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coordinates authentication windows so restricted sessions never see the application shell. */
public final class UserUiCoordinator {
    private static final String PASSWORD_CHANGED = "密码修改成功，请使用新密码重新登录";
    private static final String LOGGED_OUT = "已退出登录";
    private static final String SESSION_REPLACED = "登录已在其他位置失效，请重新登录";
    private static final String SESSION_INVALID = "登录状态已失效，请重新登录";
    private final UserClientService users;
    private final StudentClientService students;
    private final CourseClientService courses;
    private final LibraryClientService library;
    private final ShopClientService shop;
    private final ClientConnection connection;
    private LoginFrame activeLogin;
    private MainFrame activeMain;
    private SessionMonitor sessionMonitor;
    private boolean sessionEnding;

    /** Creates the client-side authentication and shell coordinator. */
    public UserUiCoordinator(UserClientService users, ClientConnection connection) {
        this(users, (StudentClientService) null, connection);
    }

    /** Creates the authentication coordinator with optional student self-service support. */
    public UserUiCoordinator(UserClientService users, StudentClientService students,
                             ClientConnection connection) {
        this.users = Objects.requireNonNull(users, "users");
        this.students = students;
        this.courses = null;
        this.library = null;
        this.shop = null;
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    /** Creates the authentication coordinator with optional course support. */
    public UserUiCoordinator(UserClientService users, CourseClientService courses,
                             ClientConnection connection) {
        this.users = Objects.requireNonNull(users, "users");
        this.students = null;
        this.courses = courses;
        this.library = null;
        this.shop = null;
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    /** Creates the production coordinator with every campus module on one connection. */
    public UserUiCoordinator(UserClientService users, StudentClientService students,
                             CourseClientService courses, LibraryClientService library,
                             ShopClientService shop, ClientConnection connection) {
        this.users = Objects.requireNonNull(users, "users");
        this.students = Objects.requireNonNull(students, "students");
        this.courses = Objects.requireNonNull(courses, "courses");
        this.library = Objects.requireNonNull(library, "library");
        this.shop = Objects.requireNonNull(shop, "shop");
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    /** Starts the authentication flow on the Swing event dispatch thread. */
    public void start() {
        onEdt(() -> showLogin(null));
    }

    private void showLogin(String notice) {
        if (activeLogin != null && activeLogin.isShowing()) {
            if (notice != null) activeLogin.showNotice(notice);
            activeLogin.toFront();
            return;
        }
        LoginFrame login = new LoginFrame(users, connection, this::acceptLogin);
        activeLogin = login;
        if (notice != null) login.showNotice(notice);
        login.setVisible(true);
    }

    private void acceptLogin(LoginResult result) {
        activeLogin = null;
        if (result.mustChangePassword()) {
            InitialPasswordChangeDialog dialog = new InitialPasswordChangeDialog(
                    null, users,
                    () -> showLogin(PASSWORD_CHANGED),
                    () -> showLogin(null));
            dialog.setVisible(true);
            return;
        }
        MainFrame main;
        if (courses != null && library != null && shop != null) {
            main = new MainFrame(result.user(), connection, students, courses, library, shop,
                    users, result.permissions(), () -> returnToLogin(activeMain, SESSION_INVALID));
        } else {
            main = new MainFrame(result.user(), connection, students);
        }
        if (courses != null && library == null) {
            main.installPage("course", new CourseUiComposition(courses, users)
                    .workspaceFor(result.user().role()));
        }
        if (courses != null) {
            bindCourseAuthenticationFailure(main);
        }
        activeMain = main;
        sessionEnding = false;
        replaceAccountPage(main, result);
        main.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                if (activeMain == main && !sessionEnding) {
                    stopSessionMonitor();
                    activeMain = null;
                }
            }
        });
        main.setVisible(true);
        startSessionMonitor(main);
    }

    private void bindCourseAuthenticationFailure(MainFrame main) {
        AtomicBoolean handedOff = new AtomicBoolean();
        Runnable remove = courses.addAuthenticationFailureListener(failure -> {
            if (!handedOff.compareAndSet(false, true)) return;
            onEdt(() -> returnToLogin(main, SESSION_INVALID));
        });
        main.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                remove.run();
            }
        });
    }

    private void replaceAccountPage(MainFrame main, LoginResult result) {
        for (java.awt.Component component : main.content().getComponents()) {
            if ("page.account".equals(component.getName())) {
                main.content().remove(component);
                break;
            }
        }
        AccountPanel account = new AccountPanel(
                users, result.user(), result.permissions(),
                () -> returnToLogin(main, PASSWORD_CHANGED),
                () -> returnToLogin(main, LOGGED_OUT),
                () -> beginSessionEnd(main),
                () -> resumeSession(main),
                () -> returnToLogin(main, SESSION_INVALID));
        main.content().add(account, "account");
        main.content().revalidate();
        main.content().repaint();
    }

    private void returnToLogin(MainFrame main, String notice) {
        beginSessionEnd(main);
        users.clearSession();
        activeMain = null;
        main.dispose();
        sessionEnding = false;
        showLogin(notice);
    }

    private void beginSessionEnd(MainFrame main) {
        if (activeMain != main || sessionEnding) return;
        sessionEnding = true;
        stopSessionMonitor();
    }

    private void resumeSession(MainFrame main) {
        if (activeMain != main || !main.isShowing()) return;
        sessionEnding = false;
        startSessionMonitor(main);
    }

    private void startSessionMonitor(MainFrame main) {
        if (sessionMonitor != null) return;
        sessionMonitor = new SessionMonitor(users, () -> sessionExpired(main));
        sessionMonitor.start();
    }

    private void sessionExpired(MainFrame main) {
        if (activeMain != main || sessionEnding || !main.isShowing()) return;
        sessionEnding = true;
        stopSessionMonitor();
        main.setEnabled(false);
        SessionReplacementWarningDialog warning = new SessionReplacementWarningDialog(
                main, () -> finishSessionReplacement(main));
        warning.showWarning();
    }

    private void finishSessionReplacement(MainFrame main) {
        if (activeMain != main || !sessionEnding) return;
        users.clearSession();
        activeMain = null;
        main.dispose();
        sessionEnding = false;
        showLogin(SESSION_REPLACED);
    }

    private void stopSessionMonitor() {
        if (sessionMonitor != null) {
            sessionMonitor.stop();
            sessionMonitor = null;
        }
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
