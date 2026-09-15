package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.core.ui.editor.*;
import edu.seu.vcampus.common.library.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Compact embedded confirmation and condition form for resolving a loan. */
public final class LoanActionPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JComboBox<ReturnCondition> condition = new JComboBox<>(new ReturnCondition[]{
            ReturnCondition.NORMAL, ReturnCondition.MINOR_DAMAGE, ReturnCondition.MAJOR_DAMAGE});

    /** Creates a return or loss action editor for the selected loan. */
    public LoanActionPanel(LoanView loan, LoanStatus resolution,
            Consumer<ReturnCondition> submit, Runnable close) {
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("借阅人：")); form.add(new JLabel(readable(loan.borrowerLoginId(), loan.borrowerUserId())));
        form.add(new JLabel("馆藏副本：")); form.add(new JLabel(readable(loan.copyBarcode(), loan.copyId())));
        if (resolution == LoanStatus.RETURNED) {
            form.add(new JLabel("归还情况：")); form.add(condition);
        }
        root.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("取消"); cancel.addActionListener(event -> close.run());
        JButton confirm = new JButton(resolution == LoanStatus.RETURNED ? "确认归还" : "确认标记遗失");
        confirm.addActionListener(event -> submit.accept(resolution == LoanStatus.LOST
                ? ReturnCondition.LOST : (ReturnCondition) condition.getSelectedItem()));
        actions.add(cancel); actions.add(confirm); root.add(actions, BorderLayout.SOUTH);
    }

    private static String readable(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return condition.getSelectedIndex() > 0; }
}
