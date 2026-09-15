package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.common.student.TrainingPlanCourseView;

/** Navigates the workspace cards: empty placeholder and course edit form. */
abstract class TrainingPlanManagementPanelWorkspaceNav extends TrainingPlanManagementPanelQueries {

    protected TrainingPlanManagementPanelWorkspaceNav(StudentClientService students) {
        super(students);
    }

    protected void showEmptyWorkspace() {
        editingCourse = null;
        isEditingPlan = false;
        courseMsgLabel.setText(" ");
        planMsgLabel.setText(" ");
        workspaceCardLayout.show(workspaceCardPanel, "EMPTY");
    }

    protected void openCourseWorkspace(TrainingPlanCourseView existing) {
        if (currentPlan == null) {
            statusLabel.setText("请先查询并选择一个培养方案");
            return;
        }
        editingCourse = existing;
        boolean isEdit = existing != null;
        courseBorder.setTitle(isEdit ? "编辑课程" : "添加课程");
        workspaceCardPanel.repaint();
        courseCodeField.setText(isEdit ? existing.courseCode() : "");
        courseNameField.setText(isEdit ? existing.courseName() : "");
        courseCreditsField.setText(isEdit ? existing.credits().toPlainString() : "2");
        courseTypeBox.setSelectedItem(isEdit ? existing.courseType() : CourseType.REQUIRED);
        courseSemesterBox.setSelectedIndex(isEdit ? Math.max(0, existing.semester() - 1) : 0);
        courseMsgLabel.setText(" ");
        workspaceCardLayout.show(workspaceCardPanel, "COURSE");
        courseCodeField.requestFocusInWindow();
    }
}
