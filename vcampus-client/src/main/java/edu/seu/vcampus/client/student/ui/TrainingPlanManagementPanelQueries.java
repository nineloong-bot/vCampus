package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.TrainingPlanQuery;

import javax.swing.*;
import java.util.List;

/** Loads departments, majors and training plans and refreshes the plan summary. */
abstract class TrainingPlanManagementPanelQueries extends TrainingPlanManagementPanelFields {

    protected TrainingPlanManagementPanelQueries(StudentClientService students) {
        super(students);
    }

    protected void loadDepartments() {
        students.listDepartments(true).thenAccept(r -> SwingUtilities.invokeLater(() -> {
            if (r.success() && r.data() != null) {
                deptBox.removeAllItems();
                r.data().forEach(deptBox::addItem);
            }
        }));
    }

    void onDepartmentChanged() {
        majorBox.removeAllItems();
        yearBox.removeAllItems();
        DepartmentView dept = (DepartmentView) deptBox.getSelectedItem();
        if (dept == null) return;
        students.listMajors(dept.departmentId()).thenAccept(r -> SwingUtilities.invokeLater(() -> {
            if (r.success() && r.data() != null) r.data().forEach(majorBox::addItem);
        }));
    }

    void onMajorChanged() {
        yearBox.removeAllItems();
        MajorView major = (MajorView) majorBox.getSelectedItem();
        if (major == null) return;
        for (int y = 2020; y <= 2030; y++) yearBox.addItem(y + "级");
    }

    void queryPlan() {
        MajorView major = (MajorView) majorBox.getSelectedItem();
        String yearStr = (String) yearBox.getSelectedItem();
        if (major == null || yearStr == null) {
            statusLabel.setText("请先选择院系、专业和年级");
            return;
        }
        int year = Integer.parseInt(yearStr.replace("级", ""));
        students.searchTrainingPlans(new TrainingPlanQuery(major.majorId(), year, 1, 10))
                .thenAccept(response -> SwingUtilities.invokeLater(() -> {
                    if (response.success() && response.data() != null && !response.data().items().isEmpty()) {
                        loadPlanDetail(response.data().items().get(0).planId());
                    } else {
                        currentPlan = null;
                        courseModel.setCourses(List.of());
                        planInfoLabel.setText(major.name() + " " + year + "级 — 暂无方案，可点击\"新建方案\"");
                    }
                }));
    }

    protected void loadPlanDetail(String planId) {
        students.getTrainingPlan(planId).thenAccept(response -> SwingUtilities.invokeLater(() -> {
            if (response.success() && response.data() != null) {
                currentPlan = response.data();
                courseModel.setCourses(currentPlan.courses());
                long required = currentPlan.courses().stream()
                        .filter(c -> c.courseType() == CourseType.REQUIRED).count();
                long elective = currentPlan.courses().stream()
                        .filter(c -> c.courseType() == CourseType.ELECTIVE).count();
                long cross = currentPlan.courses().stream()
                        .filter(c -> c.courseType() == CourseType.CROSS_DISCIPLINARY).count();
                planInfoLabel.setText(currentPlan.majorName() + " " + currentPlan.enrollmentYear()
                        + "级 — " + currentPlan.planName()
                        + " | 必修" + required + "门 选修" + elective + "门 跨学科" + cross + "门"
                        + " | 选修毕业要求≥" + currentPlan.minElectiveCount() + "门"
                        + "≥" + currentPlan.minElectiveCredits() + "学分");
                statusLabel.setText("已加载方案");
            }
        }));
    }
}
