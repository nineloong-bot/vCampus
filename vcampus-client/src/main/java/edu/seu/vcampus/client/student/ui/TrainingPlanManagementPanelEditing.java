package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.TrainingPlanCourseView;

import javax.swing.*;

/** Handles plan workspace opening, course selection removal and the two dialog flows. */
abstract class TrainingPlanManagementPanelEditing extends TrainingPlanManagementPanelWorkspaceView {

    protected TrainingPlanManagementPanelEditing(StudentClientService students) {
        super(students);
    }

    void editSelectedCourse() {
        if (currentPlan == null) {
            statusLabel.setText("请先查询并选择一个培养方案");
            return;
        }
        int row = courseTable.getSelectedRow();
        if (row < 0) {
            statusLabel.setText("请先在表格中选择一门课程");
            return;
        }
        openCourseWorkspace(courseModel.getCourse(row));
    }

    void openNewPlanWorkspace() {
        MajorView major = (MajorView) majorBox.getSelectedItem();
        String yearStr = (String) yearBox.getSelectedItem();
        if (major == null || yearStr == null) {
            statusLabel.setText("请先选择院系、专业和年级");
            return;
        }
        int year = Integer.parseInt(yearStr.replace("级", ""));
        isEditingPlan = false;
        planBorder.setTitle("新建培养方案");
        workspaceCardPanel.repaint();
        planNameField.setText(major.name() + year + "级培养方案");
        minCountField.setText("4");
        minCreditsField.setText("8");
        planMsgLabel.setText(" ");
        workspaceCardLayout.show(workspaceCardPanel, "PLAN");
        planNameField.requestFocusInWindow();
    }

    void openEditPlanWorkspace() {
        if (currentPlan == null) {
            statusLabel.setText("请先查询并选择一个方案");
            return;
        }
        isEditingPlan = true;
        planBorder.setTitle("编辑培养方案");
        workspaceCardPanel.repaint();
        planNameField.setText(currentPlan.planName());
        minCountField.setText(String.valueOf(currentPlan.minElectiveCount()));
        minCreditsField.setText(currentPlan.minElectiveCredits().toPlainString());
        planMsgLabel.setText(" ");
        workspaceCardLayout.show(workspaceCardPanel, "PLAN");
        planNameField.requestFocusInWindow();
    }

    void removeSelectedCourse() {
        if (currentPlan == null) return;
        int row = courseTable.getSelectedRow();
        if (row < 0) {
            statusLabel.setText("请先选择一门课程");
            return;
        }
        TrainingPlanCourseView course = courseModel.getCourse(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "确认删除课程 " + course.courseName() + "？", "确认删除",
                JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            students.removeTrainingPlanCourse(course.planCourseId())
                    .thenAccept(r -> SwingUtilities.invokeLater(() -> {
                        loadPlanDetail(currentPlan.planId());
                        statusLabel.setText("课程已删除");
                    }));
        }
    }

    void openCoursePoolDialog() {
        if (currentPlan == null) {
            statusLabel.setText("请先查询并选择一个培养方案");
            JOptionPane.showMessageDialog(this, "请先在左侧选择并查询一个培养方案，再从课程库引入课程。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        TrainingPlanCoursePoolDialog.show(this, students, currentPlan,
                (DepartmentView) deptBox.getSelectedItem(),
                () -> loadPlanDetail(currentPlan.planId()), statusLabel::setText);
    }

    void openCrossCourseReviewDialog() {
        TrainingPlanCrossCourseReviewDialog.show(this, students,
                () -> { if (currentPlan != null) loadPlanDetail(currentPlan.planId()); });
    }
}
