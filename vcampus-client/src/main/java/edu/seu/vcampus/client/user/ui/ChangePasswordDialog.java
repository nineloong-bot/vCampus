package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
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
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Normal-session password change dialog that requires a fresh login after success. */
public final class ChangePasswordDialog extends JDialog {
    private final UserClientService users;
    private final Runnable onChanged;
    private final JPasswordField oldPassword = field("change.old", "当前密码");
    private final JPasswordField newPassword = field("change.new", "新密码");
    private final JPasswordField confirm = field("change.confirm", "确认新密码");
    private final JButton submit = named(new JButton("修改密码"), "change.submit", "修改密码");
    private final JLabel error = named(new JLabel(" "), "change.error", "密码修改提示");

    /** Creates a modal password change dialog. */
    public ChangePasswordDialog(Window owner, UserClientService users, Runnable onChanged) {
        super(owner, "修改密码", Dialog.ModalityType.APPLICATION_MODAL);
        this.users = Objects.requireNonNull(users, "users");
        this.onChanged = Objects.requireNonNull(onChanged, "onChanged");
        setContentPane(content());
        submit.addActionListener(event -> submit());
        getRootPane().setDefaultButton(submit);
        setSize(560, 400);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JPanel content() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiColors.BACKGROUND_PAGE);
        panel.setBorder(UiBorders.pageInset());
        GridBagConstraints c = constraints();
        JLabel title = new JLabel("修改密码");
        title.setFont(UiTypography.PAGE_TITLE);
        panel.add(title, c);
        addField(panel, c, "旧密码", oldPassword, 1);
        addField(panel, c, "新密码", newPassword, 2);
        addField(panel, c, "确认密码", confirm, 3);
        c.gridy = 4;
        c.insets = new Insets(UiSpacing.SPACE_4, 0, UiSpacing.SPACE_3, 0);
        submit.setBackground(UiColors.ACCENT);
        submit.setForeground(UiColors.TEXT_ON_PRIMARY);
        panel.add(submit, c);
        c.gridy = 5;
        error.setForeground(UiColors.ERROR_FG);
        panel.add(error, c);
        return panel;
    }

    private void submit() {
        char[] oldValue = oldPassword.getPassword();
        char[] newValue = newPassword.getPassword();
        char[] confirmation = confirm.getPassword();
        clearFields();
        if (!Arrays.equals(newValue, confirmation)) {
            clear(oldValue, newValue, confirmation);
            error.setText("两次输入的新密码不一致");
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
        response.whenComplete((ignored, failure) -> onEdt(() -> finish(failure)));
    }

    private void finish(Throwable failure) {
        setBusy(false);
        if (failure != null) {
            error.setText(UserErrorMessages.operation(failure, "密码修改失败，请稍后重试"));
            return;
        }
        users.clearSession();
        dispose();
        onChanged.run();
    }

    private void setBusy(boolean busy) {
        submit.setEnabled(!busy);
        submit.setText(busy ? "正在修改…" : "修改密码");
    }
    private void clearFields() {
        oldPassword.setText(""); newPassword.setText(""); confirm.setText("");
        error.setText(" ");
    }
    private static void addField(JPanel panel, GridBagConstraints c, String label,
                                 JPasswordField field, int row) {
        c.gridy = row;
        c.insets = new Insets(UiSpacing.SPACE_3, 0, 0, 0);
        JPanel line = new JPanel(new java.awt.BorderLayout(UiSpacing.SPACE_3, 0));
        line.setOpaque(false); line.add(new JLabel(label), java.awt.BorderLayout.WEST);
        line.add(field, java.awt.BorderLayout.CENTER); panel.add(line, c);
    }
    private static GridBagConstraints constraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        return c;
    }
    private static JPasswordField field(String name, String accessibleName) {
        return named(new JPasswordField(20), name, accessibleName);
    }
    private static <T extends javax.swing.JComponent> T named(
            T value, String name, String accessibleName) {
        value.setName(name); value.getAccessibleContext().setAccessibleName(accessibleName);
        return value;
    }
    private static void clear(char[]... values) {
        for (char[] value : values) Arrays.fill(value, '\0');
    }
    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
