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

/** Package-private confirmation boundary that prevents accidental logout. */
final class LogoutConfirmationDialog extends JDialog {
    LogoutConfirmationDialog(Window owner, Runnable onConfirmed) {
        super(owner, "确认退出登录", ModalityType.APPLICATION_MODAL);
        Objects.requireNonNull(onConfirmed, "onConfirmed");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        JPanel content = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        content.setBackground(UiColors.BACKGROUND_PAGE);
        content.setBorder(UiBorders.pageInset());
        JLabel question = new JLabel("确定要退出当前登录吗？");
        question.setFont(UiTypography.BODY);
        content.add(question, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(
                FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        actions.setOpaque(false);
        JButton cancel = button("取消", "logout.cancel");
        JButton confirm = button("退出登录", "logout.confirm");
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
        String action = "cancel-logout";
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), action);
        getRootPane().getActionMap().put(action, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                cancel.doClick();
            }
        });
    }

    private static JButton button(String text, String name) {
        JButton button = new JButton(text);
        button.setName(name);
        button.getAccessibleContext().setAccessibleName(text);
        return button;
    }
}
