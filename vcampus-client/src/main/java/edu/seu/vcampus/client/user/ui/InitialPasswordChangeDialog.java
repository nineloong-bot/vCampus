package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.user.service.UserClientService;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Restricted first-login dialog that permits only password change or logout. */
public final class InitialPasswordChangeDialog extends JDialog {
    private final UserClientService users;
    private final Runnable onChanged;
    private final Runnable onExit;
    private final JPasswordField oldPassword = field("password.old", "当前密码");
    private final JPasswordField newPassword = field("password.new", "新密码");
    private final JPasswordField confirmPassword = field("password.confirm", "确认新密码");
    private final JButton submit = button("确认修改", "password.submit");
    private final JButton exit = button("退出登录", "password.exit");
    private final JLabel error = label("password.error", "密码修改提示");

    /** Creates the restricted authentication-only password change dialog. */
    public InitialPasswordChangeDialog(Window owner, UserClientService users,
                                       Runnable onChanged, Runnable onExit) {
        super(owner, "首次修改密码", Dialog.ModalityType.APPLICATION_MODAL);
        this.users = Objects.requireNonNull(users, "users");
        this.onChanged = Objects.requireNonNull(onChanged, "onChanged");
        this.onExit = Objects.requireNonNull(onExit, "onExit");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setContentPane(content());
        submit.addActionListener(event -> submitChange());
        exit.addActionListener(event -> exitLogin());
        getRootPane().setDefaultButton(submit);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { exitLogin(); }
        });
        setSize(UiDimensions.PASSWORD_DIALOG);
        setResizable(false);
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(oldPassword::requestFocusInWindow);
    }

    private JPanel content() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiColors.BACKGROUND_PAGE);
        panel.setBorder(UiBorders.pageInset());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, UiSpacing.SPACE_3, 0);
        JLabel title = new JLabel("首次修改密码");
        title.setFont(UiTypography.PAGE_TITLE);
        panel.add(title, c);
        c.gridy = 1;
        JLabel explanation = new JLabel("为保护账户安全，请修改初始密码后重新登录。");
        explanation.setForeground(UiColors.TEXT_SECONDARY);
        panel.add(explanation, c);
        addField(panel, c, "旧密码", oldPassword, 2);
        addField(panel, c, "新密码", newPassword, 3);
        addField(panel, c, "确认密码", confirmPassword, 4);
        c.gridy = 5;
        c.gridx = 0;
        c.gridwidth = 1;
        c.weightx = 1;
        c.insets = new Insets(UiSpacing.SPACE_3, 0, UiSpacing.SPACE_3, UiSpacing.SPACE_2);
        exit.setForeground(UiColors.ERROR_FG);
        panel.add(exit, c);
        c.gridx = 1;
        c.insets = new Insets(UiSpacing.SPACE_3, UiSpacing.SPACE_2, UiSpacing.SPACE_3, 0);
        submit.setBackground(UiColors.ACCENT);
        submit.setForeground(UiColors.TEXT_ON_PRIMARY);
        panel.add(submit, c);
        c.gridy = 6;
        c.gridx = 0;
        c.gridwidth = 2;
        error.setForeground(UiColors.ERROR_FG);
        panel.add(error, c);
        return panel;
    }

    private static void addField(JPanel panel, GridBagConstraints c, String label,
                                 JPasswordField field, int row) {
        c.gridy = row;
        c.gridx = 0;
        c.gridwidth = 1;
        c.weightx = 0;
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        panel.add(field, c);
    }

    private void submitChange() {
        char[] oldValue = oldPassword.getPassword();
        char[] newValue = newPassword.getPassword();
        char[] confirmation = confirmPassword.getPassword();
        clearFields();
        if (!Arrays.equals(newValue, confirmation)) {
            clear(oldValue, newValue, confirmation);
            error.setText("两次输入的新密码不一致");
            oldPassword.requestFocusInWindow();
            return;
        }
        setBusy(true);
        CompletableFuture<Void> response;
        try {
            response = users.changePassword(oldValue, newValue);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        } finally {
            clear(oldValue, newValue, confirmation);
        }
        response.whenComplete((ignored, failure) -> onEdt(() -> finishChange(failure)));
    }

    private void finishChange(Throwable failure) {
        if (!isDisplayable()) return;
        setBusy(false);
        if (failure != null) {
            error.setText(UserErrorMessages.operation(failure, "密码修改失败，请稍后重试"));
            oldPassword.requestFocusInWindow();
            return;
        }
        users.clearSession();
        dispose();
        onChanged.run();
    }

    private void exitLogin() {
        setBusy(true);
        CompletableFuture<Void> response;
        try {
            response = users.logout();
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((ignored, failure) -> onEdt(() -> {
            users.clearSession();
            dispose();
            onExit.run();
        }));
    }

    private void setBusy(boolean busy) {
        submit.setEnabled(!busy);
        exit.setEnabled(!busy);
        submit.setText(busy ? "正在修改…" : "确认修改");
    }

    private void clearFields() {
        oldPassword.setText("");
        newPassword.setText("");
        confirmPassword.setText("");
        error.setText(" ");
    }

    private static void clear(char[]... values) {
        for (char[] value : values) Arrays.fill(value, '\0');
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    private static JPasswordField field(String name, String accessibleName) {
        JPasswordField field = new JPasswordField(20);
        field.setName(name);
        field.getAccessibleContext().setAccessibleName(accessibleName);
        return field;
    }

    private static JButton button(String text, String name) {
        JButton button = new JButton(text);
        button.setName(name);
        button.getAccessibleContext().setAccessibleName(text);
        return button;
    }

    private static JLabel label(String name, String accessibleName) {
        JLabel label = new JLabel(" ");
        label.setName(name);
        label.getAccessibleContext().setAccessibleName(accessibleName);
        return label;
    }
}
