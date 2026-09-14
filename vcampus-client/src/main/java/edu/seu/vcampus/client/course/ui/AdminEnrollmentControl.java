package edu.seu.vcampus.client.course.ui;

import java.awt.FlowLayout;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.AdminEnrollStudentCommand;
import edu.seu.vcampus.common.course.OfferingSummary;

/** Compact administrator control for exceptional retake placement. */
final class AdminEnrollmentControl extends JPanel {
    private final CourseUiGateway gateway;
    private final Supplier<OfferingSummary> selectedOffering;
    private final Runnable onSuccess;
    private final Consumer<String> onError;
    private final JTextField studentNumber = new JTextField(10);
    private final JButton submit = AbstractCoursePanel.secondary("添加重修学生");

    AdminEnrollmentControl(CourseUiGateway gateway, Supplier<OfferingSummary> selectedOffering,
                           Runnable onSuccess, Consumer<String> onError) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 0));
        this.gateway = Objects.requireNonNull(gateway);
        this.selectedOffering = Objects.requireNonNull(selectedOffering);
        this.onSuccess = Objects.requireNonNull(onSuccess);
        this.onError = Objects.requireNonNull(onError);
        setOpaque(false);
        JLabel label = new JLabel("学生学号");
        label.setFont(UiTypography.BODY);
        studentNumber.setFont(UiTypography.BODY);
        studentNumber.setPreferredSize(new java.awt.Dimension(150, UiDimensions.CONTROL_HEIGHT));
        studentNumber.getAccessibleContext().setAccessibleName("学生学号");
        submit.getAccessibleContext().setAccessibleName("添加重修学生");
        submit.addActionListener(event -> submit());
        add(label);
        add(studentNumber);
        add(submit);
    }

    private void submit() {
        OfferingSummary offering = selectedOffering.get();
        if (offering == null) {
            onError.accept("请先选择要添加学生的教学班");
            return;
        }
        String number = studentNumber.getText().trim();
        if (number.isEmpty()) {
            onError.accept("请输入学生学号");
            return;
        }
        submit.setEnabled(false);
        gateway.adminEnrollStudent(new AdminEnrollStudentCommand(number, offering.offeringId()))
                .whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
                    submit.setEnabled(true);
                    if (error != null) {
                        onError.accept("添加失败，请核对学号、重修资格和教学班状态");
                        return;
                    }
                    studentNumber.setText("");
                    onSuccess.run();
                }));
    }
}
