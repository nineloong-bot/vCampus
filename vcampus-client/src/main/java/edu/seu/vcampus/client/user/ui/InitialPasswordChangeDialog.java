package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.user.service.UserClientService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Requires replacing an initial password before the user can enter the application. */
public final class InitialPasswordChangeDialog extends JDialog {
    private final UserClientService users;
    private final Runnable onComplete;
    private final JPasswordField oldPassword = named(
            new JPasswordField(20), "password-change.old");
    private final JPasswordField newPassword = named(
            new JPasswordField(20), "password-change.new");
    private final JPasswordField confirmation = named(
            new JPasswordField(20), "password-change.confirm");
    private final JButton submit = named(new JButton("修改密码"), "password-change.submit");
    private final JButton logout = named(new JButton("退出登录"), "password-change.logout");
    private final JLabel status = named(new JLabel(" "), "password-change.status");
    private final JLabel error = named(new JLabel(" "), "password-change.error");

    /** Creates the mandatory initial-password replacement dialog. */
    public InitialPasswordChangeDialog(UserClientService users, Runnable onComplete) {
        super((JFrame) null, "请修改初始密码", false);
        this.users = Objects.requireNonNull(users, "users");
        this.onComplete = Objects.requireNonNull(onComplete, "onComplete");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        form.add(new JLabel("原密码"));
        form.add(oldPassword);
        form.add(new JLabel("新密码"));
        form.add(newPassword);
        form.add(new JLabel("确认新密码"));
        form.add(confirmation);
        form.add(status);
        JPanel actions = new JPanel();
        actions.add(submit);
        actions.add(logout);
        form.add(actions);
        add(form, BorderLayout.CENTER);
        error.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        add(error, BorderLayout.SOUTH);
        submit.addActionListener(event -> submitChange());
        logout.addActionListener(event -> submitLogout());
        getRootPane().setDefaultButton(submit);
        pack();
        setLocationByPlatform(true);
    }

    private void submitChange() {
        char[] oldValue = null;
        char[] newValue = null;
        char[] confirmationValue = null;
        try {
            oldValue = oldPassword.getPassword();
            newValue = newPassword.getPassword();
            confirmationValue = confirmation.getPassword();
            clearFields();
            if (!Arrays.equals(newValue, confirmationValue)) {
                error.setText("两次输入的密码不一致，请重试");
                newPassword.requestFocusInWindow();
                return;
            }
            setPending(true, "正在修改密码…");
            error.setText(" ");
            CompletableFuture<Void> response;
            try {
                response = users.changePassword(oldValue, newValue);
            } catch (RuntimeException failure) {
                response = CompletableFuture.failedFuture(failure);
            }
            response.whenComplete((ignored, failure) -> onEdt(() -> finishChange(failure)));
        } finally {
            clearPassword(oldValue);
            clearPassword(newValue);
            clearPassword(confirmationValue);
        }
    }

    private void submitLogout() {
        setPending(true, "正在退出…");
        error.setText(" ");
        CompletableFuture<Void> response;
        try {
            response = users.logout();
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((ignored, failure) -> onEdt(() -> finishLogout(failure)));
    }

    private void finishChange(Throwable failure) {
        if (failure != null) {
            setPending(false, " ");
            error.setText("修改密码失败，请检查原密码后重试");
            oldPassword.requestFocusInWindow();
            return;
        }
        dispose();
        onComplete.run();
    }

    private void finishLogout(Throwable failure) {
        if (failure != null) {
            setPending(false, " ");
            error.setText("退出失败，请重试");
            return;
        }
        dispose();
    }

    private void setPending(boolean pending, String message) {
        submit.setEnabled(!pending);
        logout.setEnabled(!pending);
        status.setText(message);
    }

    private void clearFields() {
        oldPassword.setText("");
        newPassword.setText("");
        confirmation.setText("");
    }

    private static void clearPassword(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
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
