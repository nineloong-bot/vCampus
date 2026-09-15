package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.common.student.TrainingPlanCourseView;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** Table model listing the courses attached to the selected training plan. */
final class TrainingPlanCourseTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"课程代码", "课程名称", "学分", "类型", "开课学院", "学期", "名额"};
    private List<TrainingPlanCourseView> courses = List.of();

    void setCourses(List<TrainingPlanCourseView> courses) {
        this.courses = List.copyOf(courses);
        fireTableDataChanged();
    }

    TrainingPlanCourseView getCourse(int row) { return courses.get(row); }
    @Override public int getRowCount() { return courses.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }

    @Override public Object getValueAt(int row, int col) {
        TrainingPlanCourseView c = courses.get(row);
        return switch (col) {
            case 0 -> c.courseCode();
            case 1 -> c.courseName();
            case 2 -> c.credits();
            case 3 -> switch (c.courseType()) {
                case REQUIRED -> "必修";
                case ELECTIVE -> "选修";
                case CROSS_DISCIPLINARY -> "跨学科";
            };
            case 4 -> c.offeringDepartmentName() != null && !c.offeringDepartmentName().isBlank() ? c.offeringDepartmentName() : "-";
            case 5 -> "第" + c.semester() + "学期";
            case 6 -> c.courseType() == CourseType.CROSS_DISCIPLINARY ? (c.allocatedQuota() != null ? String.valueOf(c.allocatedQuota()) : "-") : "不限";
            default -> "";
        };
    }
}
