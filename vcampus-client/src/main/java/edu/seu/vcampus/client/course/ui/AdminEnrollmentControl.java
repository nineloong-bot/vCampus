package edu.seu.vcampus.client.course.ui;

import java.awt.FlowLayout;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.AdminEnrollStudentCommand;
import edu.seu.vcampus.common.course.OfferingSummary;

/** Compact administrator control for placing a student into a selected teaching class. */
final class AdminEnrollmentControl extends JPanel {
    private final CourseUiGateway gateway;
    private final OfferingSummary offering;
    private final Runnable onSuccess;
    private final Runnable onCancel;
    private final Consumer<String> onError;
    private final JTextField studentNumber = new JTextField(10);
    private final JButton submit = AbstractCoursePanel.secondary("确认添加");
    private boolean active;

    AdminEnrollmentControl(CourseUiGateway gateway, OfferingSummary offering,
                           Runnable onSuccess, Runnable onCancel, Consumer<String> onError) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 0));
        this.gateway = Objects.requireNonNull(gateway);
        this.offering = Objects.requireNonNull(offering);
        this.onSuccess = Objects.requireNonNull(onSuccess);
        this.onCancel = Objects.requireNonNull(onCancel);
        this.onError = Objects.requireNonNull(onError);
        setOpaque(false);
        JLabel label = new JLabel("学生学号");
        label.setFont(UiTypography.BODY);
        studentNumber.setFont(UiTypography.BODY);
        studentNumber.setPreferredSize(new java.awt.Dimension(150, UiDimensions.CONTROL_HEIGHT));
        studentNumber.getAccessibleContext().setAccessibleName("学生学号");
        submit.getAccessibleContext().setAccessibleName("确认添加学生");
        submit.addActionListener(event -> submit());
        add(label);
        add(studentNumber);
        JButton cancel = AbstractCoursePanel.secondary("取消");
        cancel.addActionListener(event -> onCancel.run());
        add(cancel);
        add(submit);
    }

    boolean isDirty() { return !studentNumber.getText().isBlank(); }
    void activate() { active = true; }
    void deactivate() { active = false; }

    private void submit() {
        String number = studentNumber.getText().trim();
        if (number.isEmpty()) {
            onError.accept("请输入学生学号");
            return;
        }
        submit.setEnabled(false);
        gateway.adminEnrollStudent(new AdminEnrollStudentCommand(number, offering.offeringId()))
                .whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
                    if (!active) return;
                    submit.setEnabled(true);
                    if (error != null) {
                        onError.accept("添加失败，请核对学号、修读状态和教学班状态");
                        return;
                    }
                    studentNumber.setText("");
                    onSuccess.run();
                }));
    }
}
