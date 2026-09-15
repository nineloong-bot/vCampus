package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.DepartmentView;

import javax.swing.*;
import java.awt.*;

/** Renders the department edit form shown on the right of the organization panel. */
abstract class OrganizationManagementPanelDepartmentForm extends OrganizationManagementPanelSaves {

    /** Creates the department form segment of the organization panel. */
    protected OrganizationManagementPanelDepartmentForm(StudentClientService students,
            edu.seu.vcampus.client.core.network.ClientConnection connection,
            boolean departmentManagementAllowed, boolean classManagementAllowed) {
        super(students, connection, departmentManagementAllowed, classManagementAllowed);
    }

    void showDepartmentForm(DepartmentView dept, boolean isNew) {
        editPanel.removeAll();
        JPanel form = buildEditForm();
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        c.anchor = GridBagConstraints.WEST;

        JTextField codeField = editField("student.org.code", dept == null ? "" : dept.code());
        JTextField nameField = editField("student.org.name", dept == null ? "" : dept.name());
        JCheckBox activeBox = new JCheckBox("启用");
        activeBox.setName("student.org.active");
        activeBox.setFont(UiTypography.BODY);
        activeBox.setOpaque(false);
        activeBox.setSelected(dept == null || dept.active());

        addFormRow(form, c, "编号", codeField, 0);
        addFormRow(form, c, "名称", nameField, 1);
        c.gridy = 2; c.gridx = 0; c.gridwidth = 2; c.fill = GridBagConstraints.NONE;
        form.add(activeBox, c);

        codeField.setEnabled(departmentManagementAllowed);
        nameField.setEnabled(departmentManagementAllowed);
        activeBox.setEnabled(departmentManagementAllowed);
        if (departmentManagementAllowed) {
            JButton saveButton = saveButton();
            saveButton.addActionListener(e -> saveDepartment(
                    codeField, nameField, activeBox, dept, isNew));
            c.gridy = 3; c.gridx = 1; c.anchor = GridBagConstraints.EAST;
            form.add(saveButton, c);
        }

        editPanel.add(form, BorderLayout.NORTH);
        editPanel.revalidate();
        editPanel.repaint();
    }
}
