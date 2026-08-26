package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.user.UserClient;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.LoginResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
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

    public LoginFrame(ClientConnection connection, Duration requestTimeout) {
        super("vCampus 登录");
        this.connection = connection;
        this.users = new UserClient(connection, requestTimeout);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(content());
        login.addActionListener(event -> submit());
        password.addActionListener(event -> submit());
        getRootPane().setDefaultButton(login);
        setSize(760, 480);
        setLocationRelativeTo(null);
    }

    private JPanel content() {
        JPanel root = new JPanel(new BorderLayout(24, 24));
        root.setBorder(BorderFactory.createEmptyBorder(44, 70, 44, 70));

        JLabel title = new JLabel("vCampus", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 30f));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints cell = new GridBagConstraints();
        cell.insets = new Insets(8, 8, 8, 8);
        cell.fill = GridBagConstraints.HORIZONTAL;
        addRow(form, cell, 0, "账号", loginId);
        addRow(form, cell, 1, "密码", password);
        cell.gridx = 0;
        cell.gridy = 2;
        cell.gridwidth = 2;
        form.add(new JLabel("演示账号：ADMIN / Admin1234", SwingConstants.CENTER), cell);
        cell.gridy = 3;
        form.add(login, cell);
        cell.gridy = 4;
        status.setForeground(new Color(40, 100, 70));
        form.add(status, cell);
        root.add(form, BorderLayout.CENTER);
        return root;
    }

    private static void addRow(JPanel panel, GridBagConstraints cell, int row,
            String label, java.awt.Component field) {
        cell.gridx = 0;
        cell.gridy = row;
        cell.gridwidth = 1;
        cell.weightx = 0;
        panel.add(new JLabel(label), cell);
        cell.gridx = 1;
        cell.weightx = 1;
        panel.add(field, cell);
    }

    private void submit() {
        char[] submittedPassword = password.getPassword();
        String submittedLoginId = loginId.getText();
        login.setEnabled(false);
        status.setForeground(new Color(70, 90, 130));
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
        status.setForeground(new Color(180, 45, 45));
        status.setText(message);
    }
}
