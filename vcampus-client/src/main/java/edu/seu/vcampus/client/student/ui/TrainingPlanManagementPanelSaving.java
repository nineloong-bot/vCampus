package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.SaveTrainingPlanCommand;
import edu.seu.vcampus.common.student.SaveTrainingPlanCourseCommand;

import javax.swing.*;
import java.math.BigDecimal;

/** Validates and saves course and plan edits made in the workspace forms. */
abstract class TrainingPlanManagementPanelSaving extends TrainingPlanManagementPanelWorkspaceNav {

    protected TrainingPlanManagementPanelSaving(StudentClientService students) {
        super(students);
    }

    protected void saveCourse() {
        if (currentPlan == null) {
            courseMsgLabel.setText("未选择有效方案");
            return;
        }
        String code = courseCodeField.getText().trim();
        String name = courseNameField.getText().trim();
        String creditsStr = courseCreditsField.getText().trim();
        if (code.isEmpty() || name.isEmpty()) {
            courseMsgLabel.setText("课程代码和名称不能为空");
            return;
        }
        BigDecimal credits;
        try {
            credits = new BigDecimal(creditsStr);
            if (credits.compareTo(BigDecimal.ZERO) <= 0) {
                courseMsgLabel.setText("学分必须大于0");
                return;
            }
        } catch (Exception ex) {
            courseMsgLabel.setText("学分格式无效");
            return;
        }
        CourseType type = (CourseType) courseTypeBox.getSelectedItem();
        int semester = courseSemesterBox.getSelectedIndex() + 1;
        SaveTrainingPlanCourseCommand cmd = new SaveTrainingPlanCourseCommand(
                currentPlan.planId(),
                editingCourse != null ? editingCourse.planCourseId() : null,
                code, name, credits, type, semester, true,
                editingCourse != null ? editingCourse.rowVersion() : 0,
                editingCourse != null ? editingCourse.courseId() : null,
                editingCourse != null ? editingCourse.offeringDepartmentId() : null,
                editingCourse != null ? editingCourse.offeringDepartmentName() : null,
                editingCourse != null ? editingCourse.allocatedQuota() : null);
        courseMsgLabel.setText("正在保存...");
        students.saveTrainingPlanCourse(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
            if (r.success()) {
                loadPlanDetail(currentPlan.planId());
                statusLabel.setText(editingCourse != null ? "课程修改成功" : "课程添加成功");
                showEmptyWorkspace();
            } else {
                courseMsgLabel.setText("保存失败: " + r.message());
                statusLabel.setText("保存失败: " + r.message());
            }
        }));
    }

    protected void savePlan() {
        MajorView major = (MajorView) majorBox.getSelectedItem();
        String yearStr = (String) yearBox.getSelectedItem();
        if (!isEditingPlan && (major == null || yearStr == null)) {
            planMsgLabel.setText("请先选择专业和年级");
            return;
        }
        String name = planNameField.getText().trim();
        String countStr = minCountField.getText().trim();
        String creditsStr = minCreditsField.getText().trim();
        if (name.isEmpty()) {
            planMsgLabel.setText("方案名称不能为空");
            return;
        }
        long minCount;
        BigDecimal minCredits;
        try {
            minCount = Long.parseLong(countStr);
            minCredits = new BigDecimal(creditsStr);
            if (minCount < 0 || minCredits.compareTo(BigDecimal.ZERO) < 0) {
                planMsgLabel.setText("门数与学分不能为负");
                return;
            }
        } catch (Exception ex) {
            planMsgLabel.setText("门数或学分数值无效");
            return;
        }

        if (isEditingPlan) {
            if (currentPlan == null) return;
            SaveTrainingPlanCommand cmd = new SaveTrainingPlanCommand(currentPlan.planId(),
                    currentPlan.majorId(), currentPlan.enrollmentYear(),
                    name, minCount, minCredits, true, currentPlan.rowVersion());
            planMsgLabel.setText("正在保存...");
            students.saveTrainingPlan(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                if (r.success()) {
                    loadPlanDetail(currentPlan.planId());
                    statusLabel.setText("方案更新成功");
                    showEmptyWorkspace();
                } else {
                    planMsgLabel.setText("更新失败: " + r.message());
                    statusLabel.setText("更新失败: " + r.message());
                }
            }));
        } else {
            int year = Integer.parseInt(yearStr.replace("级", ""));
            SaveTrainingPlanCommand cmd = new SaveTrainingPlanCommand(null, major.majorId(),
                    year, name, minCount, minCredits, true, 0);
            planMsgLabel.setText("正在创建...");
            students.saveTrainingPlan(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                if (r.success()) {
                    loadPlanDetail(r.data().planId());
                    statusLabel.setText("方案创建成功");
                    showEmptyWorkspace();
                } else {
                    planMsgLabel.setText("创建失败: " + r.message());
                    statusLabel.setText("创建失败: " + r.message());
                }
            }));
        }
    }
}
