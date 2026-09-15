package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.SaveClassCommand;
import edu.seu.vcampus.common.student.SaveDepartmentCommand;
import edu.seu.vcampus.common.student.SaveMajorCommand;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/** Validates and submits department, major and class edits. */
abstract class OrganizationManagementPanelSaves extends OrganizationManagementPanelTreeLoading {

    /** Creates the save segment of the organization panel. */
    protected OrganizationManagementPanelSaves(StudentClientService students,
            edu.seu.vcampus.client.core.network.ClientConnection connection,
            boolean departmentManagementAllowed, boolean classManagementAllowed) {
        super(students, connection, departmentManagementAllowed, classManagementAllowed);
    }

    void saveDepartment(JTextField codeField, JTextField nameField, JCheckBox activeBox,
                        DepartmentView base, boolean isNew) {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        if (code.isEmpty()) { errorLabel.setText("编号不能为空"); return; }
        if (name.isEmpty()) { errorLabel.setText("名称不能为空"); return; }
        String id = isNew || base == null ? "" : base.departmentId();
        long version = isNew || base == null ? 0 : base.rowVersion();
        long generation = requestGeneration.incrementAndGet();
        errorLabel.setText(" ");
        students.saveDepartment(new SaveDepartmentCommand(id, code, name, activeBox.isSelected(), version))
                .whenComplete((body, failure) -> onEdt(() -> {
                    if (!active || generation != requestGeneration.get()) return;
                    if (failure != null) { errorLabel.setText("保存失败，请稍后重试"); return; }
                    if (body != null && body.success()) { loadAll(); return; }
                    if (body != null && "COMMON_CONCURRENT_MODIFICATION".equals(body.code())) {
                        loadAll(() -> errorLabel.setText("数据已被修改，请刷新后重试"));
                        return;
                    }
                    errorLabel.setText(safeMessage(body));
                }));
    }

    void saveMajor(JTextField codeField, JTextField nameField, JCheckBox activeBox,
                   JCheckBox[] gradeBoxes, MajorView base, boolean isNew) {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        if (code.isEmpty()) { errorLabel.setText("编号不能为空"); return; }
        if (!MAJOR_CODE.matcher(code).matches()) { errorLabel.setText("专业编号必须为3位大写字母或数字"); return; }
        if (name.isEmpty()) { errorLabel.setText("名称不能为空"); return; }
        String departmentId = null;
        if (selectedNode != null) {
            Object nodeObj = selectedNode.getUserObject();
            if (nodeObj instanceof DepartmentView dept) {
                departmentId = dept.departmentId();
            } else if (selectedNode.getParent() != null) {
                Object parentObj = ((DefaultMutableTreeNode) selectedNode.getParent()).getUserObject();
                if (parentObj instanceof DepartmentView dept) departmentId = dept.departmentId();
            }
        }
        if (departmentId == null) { errorLabel.setText("请选择所属院系"); return; }
        StringBuilder gradesBuilder = new StringBuilder();
        for (int i = 0; i < gradeBoxes.length; i++) {
            if (gradeBoxes[i].isSelected()) {
                if (gradesBuilder.length() > 0) gradesBuilder.append(",");
                gradesBuilder.append(i + 1);
            }
        }
        String grades = gradesBuilder.length() > 0 ? gradesBuilder.toString() : null;
        String id = isNew || base == null ? "" : base.majorId();
        long version = isNew || base == null ? 0 : base.rowVersion();
        long generation = requestGeneration.incrementAndGet();
        errorLabel.setText(" ");
        students.saveMajor(new SaveMajorCommand(id, departmentId, code, name, grades, activeBox.isSelected(), version))
                .whenComplete((body, failure) -> onEdt(() -> {
                    if (!active || generation != requestGeneration.get()) return;
                    if (failure != null) { errorLabel.setText("保存失败，请稍后重试"); return; }
                    if (body != null && body.success()) { loadAll(); return; }
                    if (body != null && "COMMON_CONCURRENT_MODIFICATION".equals(body.code())) {
                        loadAll(() -> errorLabel.setText("数据已被修改，请刷新后重试"));
                        return;
                    }
                    errorLabel.setText(safeMessage(body));
                }));
    }

    void saveClass(JTextField codeField, JTextField nameField, JSpinner yearSpinner,
                   JSpinner numberSpinner, JCheckBox activeBox, ClassView base, boolean isNew) {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        int enrollmentYear = (Integer) yearSpinner.getValue();
        int classNum = (Integer) numberSpinner.getValue();
        if (code.isEmpty()) { errorLabel.setText("编号不能为空"); return; }
        if (name.isEmpty()) { errorLabel.setText("名称不能为空"); return; }
        if (enrollmentYear < 2000 || enrollmentYear > 2099) { errorLabel.setText("入学年份必须在2000-2099之间"); return; }
        if (classNum < 1 || classNum > 9) { errorLabel.setText("班级序号必须在1-9之间"); return; }
        String majorId = null;
        if (selectedNode != null) {
            Object nodeObj = selectedNode.getUserObject();
            if (nodeObj instanceof MajorView major) {
                majorId = major.majorId();
            } else if (nodeObj instanceof YearNode y) {
                majorId = y.majorId();
            } else if (selectedNode.getParent() != null) {
                Object parentObj = ((DefaultMutableTreeNode) selectedNode.getParent()).getUserObject();
                if (parentObj instanceof MajorView major) majorId = major.majorId();
                else if (parentObj instanceof YearNode y) majorId = y.majorId();
            }
        }
        if (majorId == null) { errorLabel.setText("请选择所属专业"); return; }
        String id = isNew || base == null ? "" : base.classId();
        long version = isNew || base == null ? 0 : base.rowVersion();
        long generation = requestGeneration.incrementAndGet();
        errorLabel.setText(" ");
        students.saveClass(new SaveClassCommand(id, majorId, code, name, enrollmentYear, classNum, activeBox.isSelected(), version))
                .whenComplete((body, failure) -> onEdt(() -> {
                    if (!active || generation != requestGeneration.get()) return;
                    if (failure != null) { errorLabel.setText("保存失败，请稍后重试"); return; }
                    if (body != null && body.success()) { loadAll(); return; }
                    if (body != null && "COMMON_CONCURRENT_MODIFICATION".equals(body.code())) {
                        loadAll(() -> errorLabel.setText("数据已被修改，请刷新后重试"));
                        return;
                    }
                    errorLabel.setText(safeMessage(body));
                }));
    }
}
