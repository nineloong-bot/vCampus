package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;

/** Loads departments, majors and classes into the organization tree. */
abstract class OrganizationManagementPanelTreeLoading extends OrganizationManagementPanelBase {

    /** Creates the tree loading segment of the organization panel. */
    protected OrganizationManagementPanelTreeLoading(StudentClientService students,
            edu.seu.vcampus.client.core.network.ClientConnection connection,
            boolean departmentManagementAllowed, boolean classManagementAllowed) {
        super(students, connection, departmentManagementAllowed, classManagementAllowed);
    }

    void loadAll() { loadAll(null); }

    void loadAll(Runnable onComplete) {
        long generation = requestGeneration.incrementAndGet();
        onEdt(() -> {
            if (active && generation == requestGeneration.get()) {
                statusLabel.setText("正在加载");
                errorLabel.setText(" ");
                addDeptButton.setEnabled(false);
            }
        });
        students.listDepartments(false).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure != null) {
                statusLabel.setText("加载失败");
                errorLabel.setText("组织架构加载失败，请稍后重试");
                updateAddButtons();
                return;
            }
            if (body == null || !body.success() || body.data() == null) {
                statusLabel.setText("加载失败");
                errorLabel.setText(safeMessage(body));
                updateAddButtons();
                return;
            }
            rootNode.removeAllChildren();
            treeModel.reload();
            ArrayList<DepartmentView> departments = body.data();
            for (DepartmentView dept : departments) {
                DefaultMutableTreeNode deptNode = new DefaultMutableTreeNode(dept, true);
                rootNode.add(deptNode);
                loadMajors(deptNode, dept.departmentId(), generation);
            }
            treeModel.reload();
            expandAll();
            statusLabel.setText(connection.state() == ConnectionState.CONNECTED ? "已加载" : "已断开连接");
            errorLabel.setText(" ");
            updateAddButtons();
            if (onComplete != null) onComplete.run();
        }));
    }

    private void loadMajors(DefaultMutableTreeNode deptNode, String departmentId, long generation) {
        students.listMajors(departmentId, false).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                for (MajorView major : body.data()) {
                    DefaultMutableTreeNode majorNode = new DefaultMutableTreeNode(major, true);
                    deptNode.add(majorNode);
                    if (classManagementAllowed) {
                        loadClasses(majorNode, major, generation);
                    }
                }
                treeModel.reload();
                expandAll();
            }
        }));
    }

    private void loadClasses(DefaultMutableTreeNode majorNode, MajorView major, long generation) {
        students.listClasses(major.majorId(), false).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                List<ClassView> classes = body.data();
                String grades = major.grades();
                if (grades != null && !grades.isBlank()) {
                    List<DefaultMutableTreeNode> yearNodes = new ArrayList<>();
                    for (String g : grades.split(",")) {
                        int grade = Integer.parseInt(g.trim());
                        YearNode yearNode = YearNode.of(grade, major.majorId());
                        DefaultMutableTreeNode yearTreeNode = new DefaultMutableTreeNode(yearNode, true);
                        majorNode.add(yearTreeNode);
                        yearNodes.add(yearTreeNode);
                    }
                    for (ClassView cls : classes) {
                        DefaultMutableTreeNode targetYear = null;
                        for (DefaultMutableTreeNode yn : yearNodes) {
                            YearNode y = (YearNode) yn.getUserObject();
                            if (y.enrollmentYear() == cls.enrollmentYear()) { targetYear = yn; break; }
                        }
                        if (targetYear != null) targetYear.add(new DefaultMutableTreeNode(cls, false));
                        else majorNode.add(new DefaultMutableTreeNode(cls, false));
                    }
                } else {
                    for (ClassView cls : classes) {
                        majorNode.add(new DefaultMutableTreeNode(cls, false));
                    }
                }
                treeModel.reload();
                expandAll();
            }
        }));
    }
}
