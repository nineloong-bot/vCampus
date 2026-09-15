package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.Consumer;

/** Collects and validates the reason for rejecting a profile application. */
final class ProfileRejectionEditor implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(UiSpacing.SPACE_2, UiSpacing.SPACE_2));
    private final JTextArea reason = new JTextArea(4, 24);
    private final JLabel error = new JLabel(" ");

    ProfileRejectionEditor(Consumer<String> submit, Runnable close) {
        Objects.requireNonNull(submit);
        Objects.requireNonNull(close);
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.SPACE_3, UiSpacing.SPACE_3,
                UiSpacing.SPACE_3, UiSpacing.SPACE_3));
        JLabel label = new JLabel("驳回原因（学生端可见）");
        reason.setName("student.profile.review.rejectionReason");
        reason.setLineWrap(true);
        reason.setWrapStyleWord(true);
        JPanel field = new JPanel(new BorderLayout(0, UiSpacing.SPACE_1));
        field.setOpaque(false);
        field.add(label, BorderLayout.NORTH);
        field.add(new JScrollPane(reason), BorderLayout.CENTER);
        root.add(field, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        error.setForeground(UiColors.ERROR_FG);
        JButton cancel = new JButton("取消");
        cancel.addActionListener(event -> close.run());
        JButton confirm = new JButton("确认驳回");
        confirm.setName("student.profile.review.confirmReject");
        confirm.addActionListener(event -> {
            String value = reason.getText().trim();
            if (value.isBlank()) {
                error.setText("驳回原因不能为空");
                return;
            }
            submit.accept(value);
        });
        actions.add(error); actions.add(cancel); actions.add(confirm);
        root.add(actions, BorderLayout.SOUTH);
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() { return !reason.getText().isBlank(); }
}
