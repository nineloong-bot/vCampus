package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.Window;
import java.util.function.Consumer;

/** Form layout and submit-button styling for the enrollment change segments. */
abstract class EnrollmentChangeDialogForm extends EnrollmentChangeDialogBase {

    EnrollmentChangeDialogForm(Window owner, StudentClientService students,
            StudentView initial, Consumer<StudentView> saved) {
        super(owner, students, initial, saved);
    }

    JPanel buildForm() {
        JPanel content = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        content.setBackground(UiColors.BACKGROUND_PAGE);
        content.setBorder(UiBorders.pageInset());
        JLabel title = new JLabel("变更班级");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        content.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        constraints.anchor = GridBagConstraints.WEST;
        addReadonly(form, constraints, "学生姓名", studentNameLabel, 0);
        addReadonly(form, constraints, "当前班级", currentClassLabel, 1);
        addCombo(form, constraints, "目标院系", departmentCombo, 2);
        addCombo(form, constraints, "目标专业", majorCombo, 3);
        addCombo(form, constraints, "目标班级", classCombo, 4);
        addField(form, constraints, "生效日期", effectiveDateField, 5);
        addField(form, constraints, "变更原因", reasonField, 6);
        content.add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, UiSpacing.SPACE_2));
        bottom.setOpaque(false);
        error.setForeground(UiColors.ERROR_FG);
        error.setFont(UiTypography.CAPTION);
        bottom.add(error, BorderLayout.NORTH);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        left.setOpaque(false);
        left.add(cancel);
        left.add(refresh);
        bottom.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        submit.setUI(new BasicButtonUI());
        submit.setOpaque(true);
        submit.setContentAreaFilled(true);
        submit.setBorder(SUBMIT_BORDER);
        submit.setFocusPainted(true);
        submit.setBackground(UiColors.ACCENT);
        submit.setForeground(UiColors.TEXT_ON_PRIMARY);
        submit.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { submit.setBorder(SUBMIT_FOCUS_BORDER); }
            @Override public void focusLost(FocusEvent event) { submit.setBorder(SUBMIT_BORDER); }
        });
        right.add(submit);
        bottom.add(right, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);
        return content;
    }

    private static void addReadonly(JPanel form, GridBagConstraints constraints,
                                    String title, JLabel value, int row) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(title);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        value.setFont(UiTypography.BODY);
        value.setForeground(UiColors.TEXT_PRIMARY);
        form.add(value, constraints);
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    private static void addCombo(JPanel form, GridBagConstraints constraints,
                                 String title, JComboBox<?> value, int row) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(title);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        form.add(value, constraints);
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    private static void addField(JPanel form, GridBagConstraints constraints,
                                 String title, JTextField value, int row) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(title);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        form.add(value, constraints);
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }
}
