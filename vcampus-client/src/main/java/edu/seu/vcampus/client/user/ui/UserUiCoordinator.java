package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.client.course.ui.CourseUiComposition;
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
    private final UserClientService users;
    private final CourseClientService courses;
    private final ClientConnection connection;

    /** Creates the client-side authentication and shell coordinator. */
    public UserUiCoordinator(UserClientService users, ClientConnection connection) {
        this(users, null, connection);
    }

    /** Creates the production coordinator with courses bound to the shared connection. */
    public UserUiCoordinator(UserClientService users, CourseClientService courses,
                             ClientConnection connection) {
        this.users = Objects.requireNonNull(users, "users");
        this.courses = courses;
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    /** Starts the authentication flow on the Swing event dispatch thread. */
    public void start() {
        onEdt(() -> showLogin(null));
    }

    private void showLogin(String notice) {
        LoginFrame login = new LoginFrame(users, connection, this::acceptLogin);
        if (notice != null) login.showNotice(notice);
        login.setVisible(true);
    }

    private void acceptLogin(LoginResult result) {
        if (result.mustChangePassword()) {
            InitialPasswordChangeDialog dialog = new InitialPasswordChangeDialog(
                    null, users,
                    () -> showLogin(PASSWORD_CHANGED),
                    () -> showLogin(null));
            dialog.setVisible(true);
            return;
        }
        MainFrame main = new MainFrame(result.user(), connection);
        if (courses != null) {
            main.installPage("course", new CourseUiComposition(courses, users)
                    .workspaceFor(result.user().role()));
        }
        replaceAccountPage(main, result);
        bindCourseAuthenticationFailure(main);
        main.setVisible(true);
    }

    private void bindCourseAuthenticationFailure(MainFrame main) {
        if (courses == null) return;
        AtomicBoolean handedOff = new AtomicBoolean();
        Runnable remove = courses.addAuthenticationFailureListener(failure -> {
            if (!handedOff.compareAndSet(false, true)) return;
            users.clearSession();
            SwingUtilities.invokeLater(() -> {
                main.dispose();
                showLogin("登录状态已失效，请重新登录");
            });
        });
        main.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                remove.run();
            }
        });
    }

    private void replaceAccountPage(MainFrame main, LoginResult result) {
        main.installPage("account", new AccountPanel(
                users, result.user(), result.permissions(),
                () -> returnToLogin(main, PASSWORD_CHANGED),
                () -> returnToLogin(main, LOGGED_OUT)));
    }

    private void returnToLogin(MainFrame main, String notice) {
        users.clearSession();
        main.dispose();
        showLogin(notice);
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
