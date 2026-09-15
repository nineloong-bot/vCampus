package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.StudentAcademicProfile;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

/** Form assembly and combo listeners for the admin edit dialog. */
abstract class AdminStudentInfoEditDialogForm extends AdminStudentInfoEditDialogSaving {

    /** Creates the form segment of the admin edit dialog. */
    protected AdminStudentInfoEditDialogForm(Window owner, StudentClientService students,
            StudentView initial, StudentAcademicProfile academic, Consumer<StudentView> saved) {
        super(owner, students, initial, academic, saved);
    }

    JPanel buildForm() {
        JPanel content = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        content.setBackground(UiColors.BACKGROUND_PAGE);
        content.setBorder(UiBorders.pageInset());
        JLabel title = new JLabel("编辑学籍信息");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        content.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        c.anchor = GridBagConstraints.WEST;

        addField(form, c, "学号", studentNumberField, 0);
        addComboField(form, c, "学生类别", studentTypeCombo, 1);

        Dimension comboSize = new Dimension(200, Math.max(32, studentNumberField.getPreferredSize().height));
        for (JComboBox<?> combo : new JComboBox<?>[]{departmentCombo, majorCombo, classCombo}) {
            combo.setFont(UiTypography.BODY);
            combo.setPreferredSize(comboSize);
        }
        departmentCombo.setName("student.info.department");
        departmentCombo.getAccessibleContext().setAccessibleName("院系");
        departmentCombo.addItem("全部");
        majorCombo.setName("student.info.major");
        majorCombo.getAccessibleContext().setAccessibleName("专业");
        majorCombo.addItem("全部");
        classCombo.setName("student.info.class");
        classCombo.getAccessibleContext().setAccessibleName("班级");
        classCombo.addItem("全部");

        addComboField(form, c, "院系", departmentCombo, 2);
        addComboField(form, c, "专业", majorCombo, 3);
        addComboField(form, c, "班级", classCombo, 4);
        addComboField(form, c, "学籍状态", statusCombo, 5);
        addComboField(form, c, "是否在籍", enrolledCombo, 6);
        addComboField(form, c, "是否在校", onCampusCombo, 7);
        addField(form, c, "校区", campusField, 8);
        addField(form, c, "培养层次", educationLevelField, 9);
        addField(form, c, "培养方式", trainingModeField, 10);
        addField(form, c, "学制（年）", programLengthField, 11);
        addComboField(form, c, "就读方式", attendanceModeCombo, 12);
        addField(form, c, "就读学位", degreeNameField, 13);
        addField(form, c, "就读学历", educationNameField, 14);
        addField(form, c, "预计毕业日期", expectedGraduationField, 15);
        addField(form, c, "毕业日期", graduationField, 16);
        addField(form, c, "学生来源", studentSourceField, 17);
        addField(form, c, "学习形式（研）", graduateStudyModeField, 18);
        addField(form, c, "辅导员姓名", counselorNameField, 19);
        addField(form, c, "辅导员联系方式", counselorContactField, 20);
        addField(form, c, "变更原因", reasonField, 21);

        JScrollPane scroll = new JScrollPane(form,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        content.add(scroll, BorderLayout.CENTER);

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

    void addField(JPanel form, GridBagConstraints c, String title, JTextField value, int row) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(title);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        form.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        form.add(value, c);
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    void addComboField(JPanel form, GridBagConstraints c, String title, JComboBox<?> combo, int row) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(title);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        form.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        form.add(combo, c);
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    void initComboListeners() {
        departmentCombo.addActionListener(e -> {
            if (suppressComboEvents) return;
            Object item = departmentCombo.getSelectedItem();
            String id = (item instanceof DepartmentView d) ? d.departmentId() : null;
            cascadeLoadMajors(id);
        });
        majorCombo.addActionListener(e -> {
            if (suppressComboEvents) return;
            Object item = majorCombo.getSelectedItem();
            String id = (item instanceof MajorView m) ? m.majorId() : null;
            cascadeLoadClasses(id);
        });
    }
}
