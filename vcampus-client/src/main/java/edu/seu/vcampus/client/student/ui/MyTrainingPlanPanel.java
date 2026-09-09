package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Student view of their own training plan. */
public final class MyTrainingPlanPanel extends JPanel {
    private final StudentClientService students;
    private final JLabel statusLabel = new JLabel("加载中...");
    private final JLabel planInfoLabel = new JLabel();
    private final CourseTableModel requiredModel = new CourseTableModel();
    private final CourseTableModel electiveModel = new CourseTableModel();

    public MyTrainingPlanPanel(StudentClientService students) {
        this.students = students;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buildUi();
        loadPlan();
    }

    private void buildUi() {
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        planInfoLabel.setFont(planInfoLabel.getFont().deriveFont(Font.BOLD, 16f));
        topPanel.add(planInfoLabel, BorderLayout.NORTH);
        topPanel.add(statusLabel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        JTabbedPane courseTabs = new JTabbedPane();
        courseTabs.addTab("必修课程", createCourseTablePanel(requiredModel));
        courseTabs.addTab("选修课程", createCourseTablePanel(electiveModel));
        add(courseTabs, BorderLayout.CENTER);
    }

    private JPanel createCourseTablePanel(CourseTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        JScrollPane scrollPane = new JScrollPane(table);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void loadPlan() {
        students.getMyTrainingPlan().thenAccept(response -> SwingUtilities.invokeLater(() -> {
            if (response.success() && response.data() != null) {
                TrainingPlanDetailView plan = response.data();
                planInfoLabel.setText(plan.departmentName() + " " + plan.majorName()
                        + " " + plan.enrollmentYear() + "级 培养方案 — " + plan.planName());
                List<TrainingPlanCourseView> required = new ArrayList<>();
                List<TrainingPlanCourseView> elective = new ArrayList<>();
                for (TrainingPlanCourseView c : plan.courses()) {
                    if (c.courseType() == CourseType.REQUIRED) required.add(c);
                    else elective.add(c);
                }
                requiredModel.setCourses(required);
                electiveModel.setCourses(elective);
                statusLabel.setText("必修 " + required.size() + " 门 | 选修 " + elective.size()
                        + " 门 | 毕业要求选修 ≥ " + plan.minElectiveCount() + " 门，≥ "
                        + plan.minElectiveCredits() + " 学分");
            } else {
                statusLabel.setText("暂无培养方案: " + (response.data() == null ? "" : response.message()));
            }
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> statusLabel.setText("加载失败: " + ex.getMessage()));
            return null;
        });
    }

    private static class CourseTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"课程代码", "课程名称", "学分", "建议学期"};
        private List<TrainingPlanCourseView> courses = List.of();

        void setCourses(List<TrainingPlanCourseView> courses) {
            this.courses = List.copyOf(courses);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return courses.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override public Object getValueAt(int row, int col) {
            TrainingPlanCourseView c = courses.get(row);
            return switch (col) {
                case 0 -> c.courseCode();
                case 1 -> c.courseName();
                case 2 -> c.credits();
                case 3 -> "第" + c.semester() + "学期";
                default -> "";
            };
        }
    }
}
