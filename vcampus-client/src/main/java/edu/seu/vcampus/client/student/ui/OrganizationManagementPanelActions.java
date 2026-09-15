package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;

/** Starts add and batch-assign flows from the organization toolbar buttons. */
abstract class OrganizationManagementPanelActions extends OrganizationManagementPanelSelection {

    /** Creates the actions segment of the organization panel. */
    protected OrganizationManagementPanelActions(StudentClientService students,
            edu.seu.vcampus.client.core.network.ClientConnection connection,
            boolean departmentManagementAllowed, boolean classManagementAllowed) {
        super(students, connection, departmentManagementAllowed, classManagementAllowed);
    }

    void startAddDepartment() {
        selectedNode = null;
        editingTarget = null;
        isNewItem = true;
        tree.clearSelection();
        showDepartmentForm(null, true);
        updateAddButtons();
    }

    void startAddMajor() {
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof DepartmentView)) return;
        isNewItem = true;
        showMajorForm(null, true);
    }

    void startAddClass() {
        if (selectedNode == null) return;
        Object obj = selectedNode.getUserObject();
        if (obj instanceof MajorView) {
            isNewItem = true;
            showClassForm(null, true, null);
        } else if (obj instanceof YearNode year) {
            isNewItem = true;
            showClassForm(null, true, year);
        }
    }

    void startAddStudent() {
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof ClassView cls)) return;
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
        if (parentNode == null) return;
        MajorView major = null;
        DepartmentView department = null;
        if (parentNode.getUserObject() instanceof MajorView m) {
            major = m;
            DefaultMutableTreeNode deptNode = (DefaultMutableTreeNode) parentNode.getParent();
            if (deptNode != null && deptNode.getUserObject() instanceof DepartmentView d) department = d;
        } else if (parentNode.getUserObject() instanceof YearNode) {
            DefaultMutableTreeNode majorNode = (DefaultMutableTreeNode) parentNode.getParent();
            if (majorNode != null && majorNode.getUserObject() instanceof MajorView m) {
                major = m;
                DefaultMutableTreeNode deptNode = (DefaultMutableTreeNode) majorNode.getParent();
                if (deptNode != null && deptNode.getUserObject() instanceof DepartmentView d) department = d;
            }
        }
        if (major == null || department == null) return;
        new ManualStudentCreationDialog(SwingUtilities.getWindowAncestor(this), students,
                department, major, cls).setVisible(true);
    }

    MajorView resolveMajor() {
        if (selectedNode == null) return null;
        Object obj = selectedNode.getUserObject();
        if (obj instanceof MajorView m) return m;
        if (obj instanceof YearNode) {
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedNode.getParent();
            if (parent != null && parent.getUserObject() instanceof MajorView m) return m;
        }
        return null;
    }

    void startBatchAssign() {
        if (selectedNode == null) return;
        MajorView major = resolveMajor();
        if (major == null) return;
        long gen = requestGeneration.incrementAndGet();
        errorLabel.setText("正在加载班级列表...");
        MajorView selectedMajor = major;
        students.listClasses(selectedMajor.majorId(), true).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || gen != requestGeneration.get()) return;
            if (failure != null || body == null || !body.success() || body.data() == null) {
                errorLabel.setText("无法加载班级列表");
                return;
            }
            ArrayList<ClassView> classes = body.data();
            if (classes.size() < 2) {
                errorLabel.setText("至少需要2个启用的班级才能批量分班");
                return;
            }
            errorLabel.setText(" ");
            new BatchClassAssignmentDialog(SwingUtilities.getWindowAncestor(this),
                    students, selectedMajor, classes).setVisible(true);
        }));
    }
}
