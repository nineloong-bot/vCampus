package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/** Renders the major edit form shown on the right of the organization panel. */
abstract class OrganizationManagementPanelMajorForm extends OrganizationManagementPanelDepartmentForm {

    /** Creates the major form segment of the organization panel. */
    protected OrganizationManagementPanelMajorForm(StudentClientService students,
            edu.seu.vcampus.client.core.network.ClientConnection connection,
            boolean departmentManagementAllowed, boolean classManagementAllowed) {
        super(students, connection, departmentManagementAllowed, classManagementAllowed);
    }

    void showMajorForm(MajorView major, boolean isNew) {
        editPanel.removeAll();
        JPanel form = buildEditForm();
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        c.anchor = GridBagConstraints.WEST;

        String parentName = "";
        if (selectedNode != null) {
            Object nodeObj = selectedNode.getUserObject();
            if (nodeObj instanceof DepartmentView dept) {
                parentName = dept.code() + " - " + dept.name();
            } else if (selectedNode.getParent() != null) {
                Object parentObj = ((DefaultMutableTreeNode) selectedNode.getParent()).getUserObject();
                if (parentObj instanceof DepartmentView dept) parentName = dept.code() + " - " + dept.name();
            }
        }
        JLabel parentLabel = new JLabel(parentName);
        parentLabel.setName("student.org.parent");
        parentLabel.setFont(UiTypography.BODY);
        parentLabel.setForeground(UiColors.TEXT_SECONDARY);

        JTextField codeField = editField("student.org.code", major == null ? "" : major.code());
        JTextField nameField = editField("student.org.name", major == null ? "" : major.name());
        JCheckBox activeBox = new JCheckBox("启用");
        activeBox.setName("student.org.active");
        activeBox.setFont(UiTypography.BODY);
        activeBox.setOpaque(false);
        activeBox.setSelected(major == null || major.active());

        JCheckBox grade1 = new JCheckBox("大一"); grade1.setFont(UiTypography.BODY); grade1.setOpaque(false);
        JCheckBox grade2 = new JCheckBox("大二"); grade2.setFont(UiTypography.BODY); grade2.setOpaque(false);
        JCheckBox grade3 = new JCheckBox("大三"); grade3.setFont(UiTypography.BODY); grade3.setOpaque(false);
        JCheckBox grade4 = new JCheckBox("大四"); grade4.setFont(UiTypography.BODY); grade4.setOpaque(false);
        if (major != null && major.grades() != null) {
            for (String g : major.grades().split(",")) {
                switch (g.trim()) {
                    case "1" -> grade1.setSelected(true);
                    case "2" -> grade2.setSelected(true);
                    case "3" -> grade3.setSelected(true);
                    case "4" -> grade4.setSelected(true);
                }
            }
        }
        JPanel gradesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        gradesPanel.setOpaque(false);
        gradesPanel.add(grade1); gradesPanel.add(grade2); gradesPanel.add(grade3); gradesPanel.add(grade4);

        addFormRow(form, c, "所属院系", parentLabel, 0);
        addFormRow(form, c, "编号", codeField, 1);
        addFormRow(form, c, "名称", nameField, 2);
        addFormRow(form, c, "年级", gradesPanel, 3);
        c.gridy = 4; c.gridx = 0; c.gridwidth = 2; c.fill = GridBagConstraints.NONE;
        form.add(activeBox, c);

        JButton saveButton = saveButton();
        saveButton.addActionListener(e -> saveMajor(codeField, nameField, activeBox,
                new JCheckBox[]{grade1, grade2, grade3, grade4}, major, isNew));
        c.gridy = 5; c.gridx = 1; c.anchor = GridBagConstraints.EAST;
        form.add(saveButton, c);

        editPanel.add(form, BorderLayout.NORTH);
        editPanel.revalidate();
        editPanel.repaint();
    }
}
