package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.ConnectionStatusPanel;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
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
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Accessible split-layout login window for the real-account client flow. */
public final class LoginFrame extends JFrame {
    private static final int DEMO_LOCKOUT_SECONDS = 30;
    private final UserClientService users;
    private final Consumer<LoginResult> onSuccess;
    private final JTextField loginId = named(new JTextField(20), "login.loginId", "登录标识");
    private final JPasswordField password = named(
            new JPasswordField(20), "login.password", "登录密码");
    private final JButton submit = named(new JButton("登录"), "login.submit", "登录");
    private final JButton apply = named(
            new JButton("申请教师账户"), "login.applyTeacher", "申请教师账户");
    private final JLabel status = named(new JLabel(" "), "login.status", "登录状态");
    private final JLabel error = named(new JLabel(" "), "login.error", "登录提示");
    private final Timer lockoutTimer = new Timer(1_000, event -> tickLockoutCountdown());
    private int lockoutSecondsRemaining;

    /** Creates a login window without live connection binding for compatibility. */
    public LoginFrame(UserClientService users, Consumer<LoginResult> onSuccess) {
        this(users, null, onSuccess);
    }

    /** Creates the login window with a live server-connection indicator. */
    public LoginFrame(UserClientService users, ClientConnection connection,
                      Consumer<LoginResult> onSuccess) {
        super("vCampus 登录");
        this.users = Objects.requireNonNull(users, "users");
        this.onSuccess = Objects.requireNonNull(onSuccess, "onSuccess");
        lockoutTimer.setCoalesce(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(1, 2));
        add(brandPanel());
        add(formPanel(connection));
        getRootPane().setDefaultButton(submit);
        submit.addActionListener(event -> submitLogin());
        apply.addActionListener(event -> new TeacherAccountApplicationDialog(
                this, users, () -> showNotice("申请已提交，等待管理员审核")).setVisible(true));
        setSize(UiDimensions.LOGIN_WINDOW);
        setResizable(false);
        setLocationRelativeTo(null);
        SwingUtilities.invokeLater(loginId::requestFocusInWindow);
    }

    /** Displays a safe workflow notice without clearing the retained login identifier. */
    public void showNotice(String message) {
        onEdt(() -> {
            error.setForeground(UiColors.SUCCESS_FG);
            error.setText(message == null || message.isBlank() ? " " : message);
        });
    }

    private JPanel brandPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiColors.PRIMARY);
        JPanel text = new JPanel(new GridLayout(0, 1, 0, UiSpacing.SPACE_3));
        text.setOpaque(false);
        JLabel product = new JLabel("vCampus");
        product.setFont(UiTypography.DISPLAY);
        product.setForeground(UiColors.TEXT_ON_PRIMARY);
        JLabel subtitle = new JLabel("校园服务 · 学术生活");
        subtitle.setFont(UiTypography.BODY);
        subtitle.setForeground(UiColors.TEXT_ON_PRIMARY);
        text.add(product);
        text.add(subtitle);
        panel.add(text);
        return panel;
    }

    private JPanel formPanel(ClientConnection connection) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiColors.BACKGROUND_PAGE);
        panel.setBorder(UiBorders.pageInset());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, UiSpacing.SPACE_6, 0);
        JLabel heading = new JLabel("登录校园账户");
        heading.setFont(UiTypography.PAGE_TITLE);
        panel.add(heading, c);
        addField(panel, c, "登录标识", loginId, 1);
        addField(panel, c, "密码", password, 2);
        c.gridy = 3;
        c.gridwidth = 2;
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        ConnectionStatusPanel connectionStatus = connection == null
                ? new ConnectionStatusPanel() : new ConnectionStatusPanel(connection);
        panel.add(connectionStatus, c);
        c.gridy = 4;
        panel.add(demoAccounts(), c);
        c.gridy = 5;
        submit.setBackground(UiColors.ACCENT);
        submit.setForeground(UiColors.TEXT_ON_PRIMARY);
        panel.add(submit, c);
        c.gridy = 6;
        apply.setForeground(UiColors.PRIMARY);
        panel.add(apply, c);
        c.gridy = 7;
        status.setForeground(UiColors.TEXT_SECONDARY);
        panel.add(status, c);
        c.gridy = 8;
        error.setForeground(UiColors.ERROR_FG);
        panel.add(error, c);
        return panel;
    }

    private static void addField(JPanel panel, GridBagConstraints c, String text,
                                 java.awt.Component field, int row) {
        c.gridy = row;
        c.gridwidth = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, UiSpacing.SPACE_4, UiSpacing.SPACE_3);
        panel.add(new JLabel(text), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, UiSpacing.SPACE_4, 0);
        panel.add(field, c);
        c.gridx = 0;
    }

    private void submitLogin() {
        char[] submittedPassword = password.getPassword();
        password.setText("");
        submit.setEnabled(false);
        submit.setText("正在登录…");
        status.setText("正在验证账户…");
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
        if (!isDisplayable()) return;
        status.setText(" ");
        submit.setText("登录");
        if (failure != null || result == null) {
            if (UserErrorMessages.isAccountLocked(failure)) {
                startLockoutCountdown();
                password.requestFocusInWindow();
                return;
            }
            submit.setEnabled(true);
            error.setForeground(UiColors.ERROR_FG);
            error.setText(UserErrorMessages.login(failure));
            password.requestFocusInWindow();
            return;
        }
        dispose();
        SwingUtilities.invokeLater(() -> onSuccess.accept(result));
    }

    private JPanel demoAccounts() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, UiSpacing.SPACE_1));
        panel.setOpaque(false);
        panel.getAccessibleContext().setAccessibleName("课程演示账号");
        panel.add(demoLabel("演示账号", "login.demoTitle"));
        panel.add(demoLabel("管理员：DEMO_ADMIN / DemoPassword7（首次登录需改密）",
                "login.demoAdmin"));
        panel.add(demoLabel("教师：DEMO_TEACHER / DemoPassword7", "login.demoTeacher"));
        panel.add(demoLabel("学生：DEMO_STUDENT / DemoPassword7",
                "login.demoStudent"));
        return panel;
    }

    private static JLabel demoLabel(String text, String name) {
        JLabel label = named(new JLabel(text), name, text);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        return label;
    }

    private void startLockoutCountdown() {
        lockoutTimer.stop();
        lockoutSecondsRemaining = DEMO_LOCKOUT_SECONDS;
        submit.setEnabled(false);
        error.setForeground(UiColors.ERROR_FG);
        showLockoutCountdown();
        lockoutTimer.start();
    }

    private void tickLockoutCountdown() {
        lockoutSecondsRemaining--;
        if (lockoutSecondsRemaining <= 0) {
            lockoutTimer.stop();
            submit.setEnabled(true);
            error.setText(" ");
            return;
        }
        showLockoutCountdown();
    }

    private void showLockoutCountdown() {
        error.setText("登录失败次数过多，请 " + lockoutSecondsRemaining + " 秒后再试");
    }

    /** Stops the demo countdown when the login window is closed. */
    @Override
    public void dispose() {
        lockoutTimer.stop();
        super.dispose();
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    private static <T extends java.awt.Component> T named(
            T component, String name, String accessibleName) {
        component.setName(name);
        component.getAccessibleContext().setAccessibleName(accessibleName);
        return component;
    }
}
