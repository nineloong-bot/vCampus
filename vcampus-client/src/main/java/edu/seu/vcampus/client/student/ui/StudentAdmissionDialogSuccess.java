package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.StudentAdmissionResult;

import javax.swing.*;
import java.awt.*;
import java.awt.Window;

/** Success receipt pane shown after a student is admitted. */
abstract class StudentAdmissionDialogSuccess extends StudentAdmissionDialogLoading {

    StudentAdmissionDialogSuccess(Window owner, StudentClientService students) {
        super(owner, students);
    }

    void showSuccess(StudentAdmissionResult result) {
        JPanel success = new JPanel(new GridBagLayout());
        success.setBackground(UiColors.BACKGROUND_PAGE);
        success.setBorder(UiBorders.pageInset());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 0;
        JLabel title = new JLabel("录取成功");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.SUCCESS_FG);
        success.add(title, constraints);
        constraints.gridy = 1;
        constraints.insets = new Insets(UiSpacing.SPACE_4, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        JLabel campusCardLabel = new JLabel("一卡通号: " + result.campusCardNumber());
        campusCardLabel.setName("student.admission.success.campus-card");
        campusCardLabel.setFont(UiTypography.SECTION_TITLE);
        campusCardLabel.setForeground(UiColors.TEXT_PRIMARY);
        success.add(campusCardLabel, constraints);
        constraints.gridy = 2;
        JLabel studentNumberLabel = new JLabel("学号: " + result.studentNumber());
        studentNumberLabel.setName("student.admission.success.student-number");
        studentNumberLabel.setFont(UiTypography.SECTION_TITLE);
        studentNumberLabel.setForeground(UiColors.TEXT_PRIMARY);
        success.add(studentNumberLabel, constraints);
        constraints.gridy = 3;
        JLabel hintLabel = new JLabel("初始密码为 12345678，请首次登录后修改");
        hintLabel.setName("student.admission.success.password-hint");
        hintLabel.setFont(UiTypography.BODY);
        hintLabel.setForeground(UiColors.TEXT_SECONDARY);
        success.add(hintLabel, constraints);
        constraints.gridy = 4;
        constraints.insets = new Insets(UiSpacing.SPACE_6, 0, 0, 0);
        JButton close = new JButton("确定");
        close.setName("student.admission.success.close");
        close.setFont(UiTypography.BODY);
        close.getAccessibleContext().setAccessibleName("确定");
        close.addActionListener(event -> dispose());
        success.add(close, constraints);
        setContentPane(success);
        revalidate();
        repaint();
    }
}
