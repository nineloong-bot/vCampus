package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.common.student.CoursePoolItemView;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** Table model listing courses offered by the school-wide course pool. */
final class TrainingPlanPoolTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"课程代码", "课程名称", "学分", "学时", "开课学院", "课程简介"};
    private List<CoursePoolItemView> courses = List.of();

    void setCourses(List<CoursePoolItemView> list) {
        this.courses = List.copyOf(list);
        fireTableDataChanged();
    }

    CoursePoolItemView getCourse(int row) { return courses.get(row); }
    @Override public int getRowCount() { return courses.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }

    @Override public Object getValueAt(int row, int col) {
        CoursePoolItemView c = courses.get(row);
        return switch (col) {
            case 0 -> c.courseCode();
            case 1 -> c.courseName();
            case 2 -> c.credits();
            case 3 -> c.totalHours() > 0 ? String.valueOf(c.totalHours()) : "-";
            case 4 -> c.departmentName() != null ? c.departmentName() : "-";
            case 5 -> c.description() != null ? c.description() : "-";
            default -> "";
        };
    }
}
