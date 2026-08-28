package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiThemeInstaller;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.user.UserClient;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.LoginResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Duration;
import java.util.Arrays;

/** Small login window used by the runnable authentication demo. */
public final class LoginFrame extends JFrame {
    private final ClientConnection connection;
    private final UserClient users;
    private final JTextField loginId = new JTextField("ADMIN", 18);
    private final JPasswordField password = new JPasswordField(18);
    private final JLabel status = new JLabel("已连接到服务器", SwingConstants.CENTER);
    private final JButton login = new JButton("登录");
    private final JButton register = new JButton("申请教师账号");

    public LoginFrame(ClientConnection connection, Duration requestTimeout) {
        super("vCampus 登录");
        UiThemeInstaller.install();
        this.connection = connection;
        this.users = new UserClient(connection, requestTimeout);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(content());
        login.addActionListener(event -> submit());
        register.addActionListener(event -> new TeacherAccountApplicationDialog(this, users).setVisible(true));
        password.addActionListener(event -> submit());
        getRootPane().setDefaultButton(login);
        setSize(860, 540);
        setLocationRelativeTo(null);
    }

    private JPanel content() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiColors.BACKGROUND_PAGE);

        JPanel brand = new JPanel(new GridBagLayout());
        brand.setBackground(UiColors.PRIMARY);
        brand.setPreferredSize(new Dimension(300, 0));
        GridBagConstraints brandCell = new GridBagConstraints();
        brandCell.gridx = 0;
        brandCell.gridy = 0;
        brandCell.insets = new Insets(0, UiSpacing.XL, UiSpacing.SM, UiSpacing.XL);
        JLabel brandTitle = new JLabel("vCampus");
        brandTitle.setFont(UiTypography.DISPLAY.deriveFont(30f));
        brandTitle.setForeground(UiColors.TEXT_ON_PRIMARY);
        brand.add(brandTitle, brandCell);
        brandCell.gridy = 1;
        brandCell.insets = new Insets(0, UiSpacing.XL, 0, UiSpacing.XL);
        JLabel brandSubtitle = new JLabel("虚拟校园 · 一站式校园服务");
        brandSubtitle.setFont(UiTypography.BODY);
        brandSubtitle.setForeground(UiColors.TEXT_ON_PRIMARY);
        brand.add(brandSubtitle, brandCell);
        root.add(brand, BorderLayout.WEST);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiColors.BACKGROUND_PAGE);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(54, 54, 54, 54),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        GridBagConstraints cell = new GridBagConstraints();
        cell.insets = new Insets(UiSpacing.SM, UiSpacing.SM, UiSpacing.SM, UiSpacing.SM);
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.weightx = 1;
        JLabel title = new JLabel("欢迎回来");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        cell.gridx = 0;
        cell.gridy = 0;
        cell.gridwidth = 2;
        cell.insets = new Insets(0, 8, 4, 8);
        form.add(title, cell);
        JLabel hint = new JLabel("登录后进入你的虚拟校园");
        hint.setFont(UiTypography.BODY);
        hint.setForeground(UiColors.TEXT_SECONDARY);
        cell.gridy = 1;
        cell.insets = new Insets(0, 8, 20, 8);
        form.add(hint, cell);
        addRow(form, cell, 2, "账号", loginId);
        addRow(form, cell, 3, "密码", password);
        cell.gridx = 0;
        cell.gridy = 4;
        cell.gridwidth = 2;
        JLabel demo = new JLabel("演示账号：ADMIN / Admin1234", SwingConstants.CENTER);
        demo.setFont(UiTypography.CAPTION);
        demo.setForeground(UiColors.TEXT_SECONDARY);
        form.add(demo, cell);
        cell.gridy = 5;
        cell.insets = new Insets(16, 8, 8, 8);
        stylePrimary(login);
        form.add(login, cell);
        cell.gridy = 6;
        styleSecondary(register);
        form.add(register, cell);
        cell.gridy = 7;
        cell.insets = new Insets(12, 8, 0, 8);
        status.setFont(UiTypography.CAPTION);
        status.setForeground(UiColors.SUCCESS_FG);
        form.add(status, cell);
        root.add(form, BorderLayout.CENTER);
        return root;
    }

    private static void addRow(JPanel panel, GridBagConstraints cell, int row,
            String label, JComponent field) {
        cell.gridx = 0;
        cell.gridy = row;
        cell.gridwidth = 1;
        cell.weightx = 0;
        panel.add(new JLabel(label), cell);
        cell.gridx = 1;
        cell.weightx = 1;
        field.setFont(UiTypography.BODY);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                UiBorders.SECTION, BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        panel.add(field, cell);
    }

    private static void stylePrimary(JButton button) {
        button.setFont(UiTypography.BODY_BOLD);
        button.setForeground(UiColors.TEXT_ON_PRIMARY);
        button.setBackground(UiColors.PRIMARY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
    }

    private static void styleSecondary(JButton button) {
        button.setFont(UiTypography.BODY);
        button.setForeground(UiColors.PRIMARY);
        button.setBackground(UiColors.BACKGROUND_SUBTLE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
    }

    private void submit() {
        char[] submittedPassword = password.getPassword();
        String submittedLoginId = loginId.getText();
        login.setEnabled(false);
        status.setForeground(UiColors.TEXT_SECONDARY);
        status.setText("正在登录…");
        users.login(submittedLoginId, submittedPassword)
                .whenComplete((response, error) -> {
                    Arrays.fill(submittedPassword, '\0');
                    SwingUtilities.invokeLater(() -> complete(response, error));
                });
    }

    private void complete(ResponseBody<LoginResult> response, Throwable error) {
        login.setEnabled(true);
        if (error != null) {
            showError("网络异常，请确认服务端正在运行");
            return;
        }
        if (!response.success()) {
            showError(response.message());
            password.selectAll();
            password.requestFocusInWindow();
            return;
        }
        connection.setSessionToken(response.data().sessionToken());
        dispose();
        new MainFrame(response.data().user()).setVisible(true);
    }

    private void showError(String message) {
        status.setForeground(UiColors.ACCENT);
        status.setText(message);
    }
}
