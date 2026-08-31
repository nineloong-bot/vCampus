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
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Public teacher-account application form with no caller-selectable role. */
public final class TeacherAccountApplicationDialog extends JDialog {
    private final UserClientService users;
    private final Runnable onSubmitted;
    private final JTextField loginId = field(new JTextField(20), "teacher.loginId", "登录标识");
    private final JPasswordField password = field(
            new JPasswordField(20), "teacher.password", "申请密码");
    private final JPasswordField confirm = field(
            new JPasswordField(20), "teacher.confirm", "确认密码");
    private final JButton submit = button("提交申请", "teacher.submit");
    private final JLabel error = label("teacher.error", "申请提示");

    /** Creates the modal public registration dialog. */
    public TeacherAccountApplicationDialog(
            Window owner, UserClientService users, Runnable onSubmitted) {
        super(owner, "申请教师账户", Dialog.ModalityType.APPLICATION_MODAL);
        this.users = Objects.requireNonNull(users, "users");
        this.onSubmitted = Objects.requireNonNull(onSubmitted, "onSubmitted");
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
        GridBagConstraints c = base();
        JLabel title = new JLabel("申请教师账户");
        title.setFont(UiTypography.PAGE_TITLE);
        panel.add(title, c);
        add(panel, c, "登录标识", loginId, 1);
        add(panel, c, "密码", password, 2);
        add(panel, c, "确认密码", confirm, 3);
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
        char[] secret = password.getPassword();
        char[] confirmation = confirm.getPassword();
        password.setText("");
        confirm.setText("");
        if (!Arrays.equals(secret, confirmation)) {
            clear(secret, confirmation);
            error.setText("两次输入的密码不一致");
            return;
        }
        setBusy(true);
        CompletableFuture<?> response;
        try {
            response = users.applyForTeacherAccount(loginId.getText(), secret);
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        } finally {
            clear(secret, confirmation);
        }
        response.whenComplete((ignored, failure) -> onEdt(() -> finish(failure)));
    }

    private void finish(Throwable failure) {
        setBusy(false);
        if (failure != null) {
            error.setText(UserErrorMessages.operation(failure, "申请未能提交，请稍后重试"));
            return;
        }
        error.setForeground(UiColors.SUCCESS_FG);
        error.setText("申请已提交，等待管理员审核");
        dispose();
        onSubmitted.run();
    }

    private void setBusy(boolean busy) {
        submit.setEnabled(!busy);
        submit.setText(busy ? "正在提交…" : "提交申请");
    }

    private static void add(JPanel panel, GridBagConstraints c, String name,
                            java.awt.Component field, int row) {
        c.gridy = row;
        c.insets = new Insets(UiSpacing.SPACE_3, 0, 0, 0);
        JPanel line = new JPanel(new java.awt.BorderLayout(UiSpacing.SPACE_3, 0));
        line.setOpaque(false);
        line.add(new JLabel(name), java.awt.BorderLayout.WEST);
        line.add(field, java.awt.BorderLayout.CENTER);
        panel.add(line, c);
    }

    private static GridBagConstraints base() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        return c;
    }

    private static <T extends javax.swing.JComponent> T field(
            T field, String name, String accessibleName) {
        field.setName(name); field.getAccessibleContext().setAccessibleName(accessibleName);
        return field;
    }
    private static JButton button(String text, String name) {
        JButton button = field(new JButton(text), name, text);
        return button;
    }
    private static JLabel label(String name, String accessibleName) {
        return field(new JLabel(" "), name, accessibleName);
    }
    private static void clear(char[]... values) {
        for (char[] value : values) Arrays.fill(value, '\0');
    }
    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
