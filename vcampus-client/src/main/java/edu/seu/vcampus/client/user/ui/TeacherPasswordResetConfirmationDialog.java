package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;

/** Package-private confirmation boundary for teacher password initialization. */
final class TeacherPasswordResetConfirmationDialog extends JDialog {
    TeacherPasswordResetConfirmationDialog(Window owner, Runnable onConfirmed) {
        super(owner, "确认初始化密码", ModalityType.APPLICATION_MODAL);
        Objects.requireNonNull(onConfirmed, "onConfirmed");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        JPanel content = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        content.setBackground(UiColors.BACKGROUND_PAGE);
        content.setBorder(UiBorders.pageInset());
        JLabel question = new JLabel(
                "将该教师密码初始化为系统默认密码，教师下次登录必须修改密码。是否继续？");
        question.setFont(UiTypography.BODY);
        content.add(question, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(
                FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        actions.setOpaque(false);
        JButton cancel = button("取消", "teacherReset.cancel");
        JButton confirm = button("初始化密码", "teacherReset.confirm");
        confirm.setForeground(UiColors.ERROR_FG);
        cancel.addActionListener(event -> dispose());
        confirm.addActionListener(event -> {
            dispose();
            onConfirmed.run();
        });
        actions.add(cancel);
        actions.add(confirm);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(cancel);
        installEscape(cancel);
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(cancel::requestFocusInWindow);
    }

    private void installEscape(JButton cancel) {
        String action = "cancel-teacher-password-reset";
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), action);
        getRootPane().getActionMap().put(action, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { cancel.doClick(); }
        });
    }

    private static JButton button(String text, String name) {
        JButton button = new JButton(text);
        button.setName(name);
        button.getAccessibleContext().setAccessibleName(text);
        return button;
    }
}
