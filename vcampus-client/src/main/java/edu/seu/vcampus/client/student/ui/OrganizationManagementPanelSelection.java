package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;

/** Reacts to organization tree selections and shows the matching editor form. */
abstract class OrganizationManagementPanelSelection extends OrganizationManagementPanelClassForms {

    /** Creates the selection segment of the organization panel. */
    protected OrganizationManagementPanelSelection(StudentClientService students,
            edu.seu.vcampus.client.core.network.ClientConnection connection,
            boolean departmentManagementAllowed, boolean classManagementAllowed) {
        super(students, connection, departmentManagementAllowed, classManagementAllowed);
    }

    void treeSelectionChanged(javax.swing.event.TreeSelectionEvent e) {
        TreePath path = e.getNewLeadSelectionPath();
        if (path == null) {
            selectedNode = null;
            editingTarget = null;
            isNewItem = false;
            showPlaceholder();
            updateAddButtons();
            return;
        }
        selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = selectedNode.getUserObject();
        if (userObject instanceof DepartmentView dept) {
            editingTarget = dept;
            isNewItem = false;
            showDepartmentForm(dept, false);
        } else if (userObject instanceof MajorView major) {
            editingTarget = major;
            isNewItem = false;
            showMajorForm(major, false);
        } else if (userObject instanceof YearNode year) {
            editingTarget = year;
            isNewItem = false;
            showYearInfo(year);
        } else if (userObject instanceof ClassView cls) {
            editingTarget = cls;
            isNewItem = false;
            showClassForm(cls, false, null);
        } else {
            selectedNode = null;
            editingTarget = null;
            isNewItem = false;
            showPlaceholder();
        }
        updateAddButtons();
    }

    void showPlaceholder() {
        editPanel.removeAll();
        JLabel placeholder = new JLabel("请选择要编辑的项目");
        placeholder.setFont(UiTypography.SECTION_TITLE);
        placeholder.setForeground(UiColors.TEXT_SECONDARY);
        placeholder.setHorizontalAlignment(SwingConstants.CENTER);
        editPanel.add(placeholder, BorderLayout.CENTER);
        editPanel.revalidate();
        editPanel.repaint();
    }
}
