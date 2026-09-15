package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/** Renders the year summary and class edit forms of the organization panel. */
abstract class OrganizationManagementPanelClassForms extends OrganizationManagementPanelMajorForm {

    /** Creates the class forms segment of the organization panel. */
    protected OrganizationManagementPanelClassForms(StudentClientService students,
            edu.seu.vcampus.client.core.network.ClientConnection connection,
            boolean departmentManagementAllowed, boolean classManagementAllowed) {
        super(students, connection, departmentManagementAllowed, classManagementAllowed);
    }

    void showYearInfo(YearNode year) {
        editPanel.removeAll();
        JPanel info = new JPanel(new GridBagLayout());
        info.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;

        JLabel title = new JLabel(year.displayName());
        title.setFont(UiTypography.SECTION_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        info.add(title, c);

        c.gridy = 1;
        JLabel hint = new JLabel("点击「新增班级」为此年级添加班级");
        hint.setFont(UiTypography.CAPTION);
        hint.setForeground(UiColors.TEXT_SECONDARY);
        info.add(hint, c);

        editPanel.add(info, BorderLayout.NORTH);
        editPanel.revalidate();
        editPanel.repaint();
    }

    void showClassForm(ClassView cls, boolean isNew, YearNode yearContext) {
        editPanel.removeAll();
        JPanel form = buildEditForm();
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        c.anchor = GridBagConstraints.WEST;

        String parentName = "";
        if (yearContext != null) {
            parentName = yearContext.displayName();
        } else if (selectedNode != null) {
            Object nodeObj = selectedNode.getUserObject();
            if (nodeObj instanceof MajorView major) {
                parentName = major.code() + " - " + major.name();
            } else if (selectedNode.getParent() != null) {
                Object parentObj = ((DefaultMutableTreeNode) selectedNode.getParent()).getUserObject();
                if (parentObj instanceof MajorView major) parentName = major.code() + " - " + major.name();
            }
        }
        JLabel parentLabel = new JLabel(parentName);
        parentLabel.setName("student.org.parent");
        parentLabel.setFont(UiTypography.BODY);
        parentLabel.setForeground(UiColors.TEXT_SECONDARY);

        JTextField codeField = editField("student.org.code", cls == null ? "" : cls.code());
        JTextField nameField = editField("student.org.name", cls == null ? "" : cls.name());
        int defaultYear = yearContext != null ? yearContext.enrollmentYear() : (cls == null ? 2025 : cls.enrollmentYear());
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(defaultYear, 2000, 2099, 1));
        yearSpinner.setName("student.org.year");
        yearSpinner.setFont(UiTypography.BODY);
        yearSpinner.getAccessibleContext().setAccessibleName("入学年份");
        if (yearContext != null) yearSpinner.setEnabled(false);
        JSpinner numberSpinner = new JSpinner(new SpinnerNumberModel(
                cls == null ? 1 : cls.classNumber(), 1, 9, 1));
        numberSpinner.setName("student.org.number");
        numberSpinner.setFont(UiTypography.BODY);
        numberSpinner.getAccessibleContext().setAccessibleName("班级序号");
        JCheckBox activeBox = new JCheckBox("启用");
        activeBox.setName("student.org.active");
        activeBox.setFont(UiTypography.BODY);
        activeBox.setOpaque(false);
        activeBox.setSelected(cls == null || cls.active());

        addFormRow(form, c, "所属专业", parentLabel, 0);
        addFormRow(form, c, "编号", codeField, 1);
        addFormRow(form, c, "名称", nameField, 2);
        addFormRow(form, c, "入学年份", yearSpinner, 3);
        addFormRow(form, c, "班级序号", numberSpinner, 4);
        c.gridy = 5; c.gridx = 0; c.gridwidth = 2; c.fill = GridBagConstraints.NONE;
        form.add(activeBox, c);

        JButton saveButton = saveButton();
        saveButton.addActionListener(e -> saveClass(codeField, nameField, yearSpinner, numberSpinner, activeBox, cls, isNew));
        c.gridy = 6; c.gridx = 1; c.anchor = GridBagConstraints.EAST;
        form.add(saveButton, c);

        editPanel.add(form, BorderLayout.NORTH);
        editPanel.revalidate();
        editPanel.repaint();
    }
}
