package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.user.LoginResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Minimal login window for the real-account demo. */
public final class LoginFrame extends JFrame {
    private final UserClientService users;
    private final Consumer<LoginResult> onAuthenticated;
    private final Consumer<LoginResult> onPasswordChangeRequired;
    private final JTextField loginId = named(new JTextField(20), "login.loginId");
    private final JPasswordField password = named(
            new JPasswordField(20), "login.password");
    private final JButton submit = named(new JButton("登录"), "login.submit");
    private final JLabel status = named(new JLabel(" "), "login.status");
    private final JLabel error = named(new JLabel(" "), "login.error");

    /** Creates a login window and its successful-login handoff. */
    public LoginFrame(UserClientService users, Consumer<LoginResult> onSuccess) {
        this(users, onSuccess, null);
    }

    /** Creates a login window with separate normal and restricted-login handoffs. */
    public LoginFrame(UserClientService users, Consumer<LoginResult> onAuthenticated,
            Consumer<LoginResult> onPasswordChangeRequired) {
        super("vCampus 登录");
        this.users = Objects.requireNonNull(users, "users");
        this.onAuthenticated = Objects.requireNonNull(onAuthenticated, "onAuthenticated");
        this.onPasswordChangeRequired = onPasswordChangeRequired;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        form.add(new JLabel("登录标识"));
        form.add(loginId);
        form.add(new JLabel("密码"));
        form.add(password);
        form.add(status);
        form.add(submit);
        add(form, BorderLayout.CENTER);
        error.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        add(error, BorderLayout.SOUTH);
        submit.addActionListener(event -> submitLogin());
        getRootPane().setDefaultButton(submit);
        pack();
        setLocationByPlatform(true);
    }

    private void submitLogin() {
        char[] submittedPassword = password.getPassword();
        password.setText("");
        submit.setEnabled(false);
        status.setText("正在登录…");
        error.setText(" ");

        CompletableFuture<LoginResult> response;
        try {
            response = users.login(loginId.getText(), submittedPassword);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        } finally {
            Arrays.fill(submittedPassword, '\0');
        }
        response.whenComplete((result, failure) -> onEdt(() -> finish(result, failure)));
    }

    private void finish(LoginResult result, Throwable failure) {
        status.setText(" ");
        if (failure != null || result == null) {
            submit.setEnabled(true);
            error.setText("用户名或密码错误，请重试");
            password.requestFocusInWindow();
            return;
        }
        if (result.mustChangePassword()) {
            if (onPasswordChangeRequired == null) {
                submit.setEnabled(true);
                error.setText("请先修改初始密码");
                password.requestFocusInWindow();
                return;
            }
            dispose();
            onPasswordChangeRequired.accept(result);
        } else {
            dispose();
            onAuthenticated.accept(result);
        }
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    private static <T extends java.awt.Component> T named(T component, String name) {
        component.setName(name);
        return component;
    }
}
