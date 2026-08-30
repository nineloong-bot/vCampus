package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.user.LoginResult;

import javax.swing.SwingUtilities;
import java.util.Objects;

/** Coordinates authentication windows so restricted sessions never see the application shell. */
public final class UserUiCoordinator {
    private static final String PASSWORD_CHANGED = "密码修改成功，请使用新密码重新登录";
    private final UserClientService users;
    private final StudentClientService students;
    private final ClientConnection connection;

    /** Creates the client-side authentication and shell coordinator. */
    public UserUiCoordinator(UserClientService users, ClientConnection connection) {
        this(users, null, connection);
    }

    /** Creates the authentication coordinator with optional student self-service support. */
    public UserUiCoordinator(UserClientService users, StudentClientService students,
                             ClientConnection connection) {
        this.users = Objects.requireNonNull(users, "users");
        this.students = students;
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
        MainFrame main = new MainFrame(result.user(), connection, students);
        main.setVisible(true);
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
