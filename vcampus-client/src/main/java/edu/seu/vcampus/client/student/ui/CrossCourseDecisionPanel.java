package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.*;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/** Embedded approval or rejection form for one cross-discipline request. */
final class CrossCourseDecisionPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(UiSpacing.SPACE_2, UiSpacing.SPACE_2));
    private final JTextField quota = new JTextField(8);
    private final JTextArea reason = new JTextArea(4, 24);
    private final JLabel status = new JLabel(" ");
    private final String initialQuota;

    CrossCourseDecisionPanel(StudentClientService students, CrossCourseApplicationView application,
            boolean approve, Runnable completed, Runnable close) {
        Objects.requireNonNull(students); Objects.requireNonNull(application);
        initialQuota = approve ? String.valueOf(application.requestedQuota()) : "";
        root.setBackground(UiColors.BACKGROUND_PAGE); root.setBorder(UiBorders.pageInset());
        JPanel form = new JPanel(new GridBagLayout()); form.setOpaque(false);
        add(form, 0, "申请学院", new JLabel(application.targetDepartmentName()));
        add(form, 1, "课程", new JLabel(application.courseName() + " (" + application.courseCode() + ")"));
        if (approve) {
            quota.setText(initialQuota); quota.setName("cross.review.quota");
            add(form, 2, "分配名额", quota);
        } else {
            reason.setLineWrap(true); reason.setWrapStyleWord(true); reason.setName("cross.review.reason");
            add(form, 2, "驳回原因", new JScrollPane(reason));
        }
        root.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setOpaque(false);
        status.setForeground(UiColors.ERROR_FG); JButton cancel = new JButton("取消");
        cancel.addActionListener(event -> close.run()); JButton submit = new JButton(approve ? "确认同意" : "确认驳回");
        submit.addActionListener(event -> submit(students, application, approve, submit, completed, close));
        actions.add(status); actions.add(cancel); actions.add(submit); root.add(actions, BorderLayout.SOUTH);
    }

    private void submit(StudentClientService students, CrossCourseApplicationView application,
            boolean approve, JButton button, Runnable completed, Runnable close) {
        Integer allocated = null; String comment = null;
        if (approve) {
            try { allocated = Integer.valueOf(quota.getText().trim()); }
            catch (NumberFormatException failure) { status.setText("分配名额必须是正整数"); return; }
            if (allocated <= 0) { status.setText("分配名额必须是正整数"); return; }
        } else {
            comment = reason.getText().trim();
            if (comment.isBlank()) { status.setText("驳回原因不能为空"); return; }
        }
        button.setEnabled(false); status.setText("正在提交…");
        students.reviewCrossCourseApplication(new ReviewCrossCourseApplicationCommand(
                        application.applicationId(), approve, allocated, comment))
                .whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
                    if (failure != null || response == null || !response.success()) {
                        status.setText(response != null && response.message() != null
                                ? response.message() : "审批操作失败"); button.setEnabled(true); return;
                    }
                    completed.run(); close.run();
                }));
    }

    private static void add(JPanel form, int row, String title, Component field) {
        GridBagConstraints c = new GridBagConstraints(); c.gridy = row; c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0; c.anchor = GridBagConstraints.WEST; form.add(new JLabel(title + "："), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; form.add(field, c);
    }
    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() {
        return !reason.getText().isBlank() || !quota.getText().trim().equals(initialQuota);
    }
}
