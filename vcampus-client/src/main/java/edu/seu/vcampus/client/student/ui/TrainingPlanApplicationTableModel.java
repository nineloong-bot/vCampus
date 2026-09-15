package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.common.student.CrossCourseApplicationView;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** Table model listing cross-disciplinary course import applications under review. */
final class TrainingPlanApplicationTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"课程代码", "课程名称", "开课学院", "申请方案", "申请学院", "学期", "申请名额", "分配名额", "状态", "申请理由", "驳回原因"};
    private List<CrossCourseApplicationView> applications = List.of();

    void setApplications(List<CrossCourseApplicationView> list) {
        this.applications = List.copyOf(list);
        fireTableDataChanged();
    }

    CrossCourseApplicationView getApplication(int row) { return applications.get(row); }
    @Override public int getRowCount() { return applications.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }

    @Override public Object getValueAt(int row, int col) {
        CrossCourseApplicationView a = applications.get(row);
        return switch (col) {
            case 0 -> a.courseCode();
            case 1 -> a.courseName();
            case 2 -> a.offeringDepartmentName();
            case 3 -> a.targetPlanName();
            case 4 -> a.targetDepartmentName();
            case 5 -> "第" + a.semester() + "学期";
            case 6 -> a.requestedQuota();
            case 7 -> a.allocatedQuota() != null ? a.allocatedQuota() : "-";
            case 8 -> switch (a.status()) {
                case PENDING -> "待审批";
                case APPROVED -> "已同意";
                case REJECTED -> "已驳回";
            };
            case 9 -> a.reason() != null ? a.reason() : "-";
            case 10 -> a.reviewComment() != null ? a.reviewComment() : "-";
            default -> "";
        };
    }
}
