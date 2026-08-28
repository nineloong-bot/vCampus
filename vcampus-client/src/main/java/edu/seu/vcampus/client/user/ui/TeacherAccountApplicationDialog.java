package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.user.UserClient;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.GridLayout;
import java.util.Arrays;

/** Minimal public registration dialog; registration always creates a pending teacher. */
public final class TeacherAccountApplicationDialog extends JDialog {
    private final UserClient users;
    private final JTextField loginId = new JTextField();
    private final JPasswordField password = new JPasswordField();
    private final JLabel status = new JLabel("注册后需由管理员审核", SwingConstants.CENTER);
    private final JButton submit = new JButton("提交教师账号申请");

    public TeacherAccountApplicationDialog(java.awt.Window owner, UserClient users) {
        super(owner, "教师账号申请", ModalityType.APPLICATION_MODAL);
        this.users = users;
        setLayout(new GridLayout(4, 2, 8, 8));
        setResizable(false);
        ((javax.swing.JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(new JLabel("登录标识")); add(loginId);
        add(new JLabel("密码")); add(password);
        add(status); add(submit);
        JButton cancel = new JButton("取消");
        add(new JLabel()); add(cancel);
        submit.addActionListener(event -> submit());
        cancel.addActionListener(event -> dispose());
        getRootPane().setDefaultButton(submit);
        setSize(420, 190);
        setLocationRelativeTo(owner);
    }

    private void submit() {
        char[] submitted = password.getPassword();
        submit.setEnabled(false);
        status.setText("正在提交…");
        users.registerTeacher(loginId.getText(), submitted).whenComplete((response, error) -> {
            Arrays.fill(submitted, '\0');
            SwingUtilities.invokeLater(() -> complete(response, error));
        });
    }

    private void complete(ResponseBody<UserView> response, Throwable error) {
        if (error != null) { status.setText("网络异常，请检查服务端"); submit.setEnabled(true); return; }
        if (!response.success()) { status.setText(response.message()); submit.setEnabled(true); return; }
        status.setText("申请已提交，账号状态为待审核");
        submit.setEnabled(false);
    }
}
