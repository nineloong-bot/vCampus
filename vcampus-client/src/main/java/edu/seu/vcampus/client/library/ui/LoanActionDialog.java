package edu.seu.vcampus.client.library.ui;
import javax.swing.*;
import java.awt.*;
public final class LoanActionDialog extends JDialog {
    public LoanActionDialog(Window owner, String action, String subject) {
        this(owner, action, subject, null);
    }
    public LoanActionDialog(Window owner, String action, String subject, Runnable confirmed) {
        super(owner, action, ModalityType.APPLICATION_MODAL);
        setName("library.loan-action-dialog");
        add(new JLabel(action + "：" + subject, JLabel.CENTER), BorderLayout.CENTER);
        JButton close = new JButton("取消"); JButton confirm = new JButton("确认" + action);
        close.addActionListener(e -> dispose());
        confirm.addActionListener(e -> { confirm.setEnabled(false); dispose(); if (confirmed != null) confirmed.run(); });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.add(close); actions.add(confirm);
        add(actions, BorderLayout.SOUTH); getRootPane().setDefaultButton(confirm);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(420, 180); setLocationRelativeTo(owner);
    }
}
