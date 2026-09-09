package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Student view of their own transcript. */
public final class MyTranscriptPanel extends JPanel {
    private final StudentClientService students;
    private final JLabel statusLabel = new JLabel("加载中...");
    private final JLabel summaryLabel = new JLabel();
    private final GradeTableModel gradeModel = new GradeTableModel();

    public MyTranscriptPanel(StudentClientService students) {
        this.students = students;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buildUi();
        loadTranscript();
    }

    private void buildUi() {
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD, 14f));
        topPanel.add(summaryLabel, BorderLayout.NORTH);
        topPanel.add(statusLabel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        JTable table = new JTable(gradeModel);
        table.setRowHeight(26);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(50);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);
        table.getColumnModel().getColumn(4).setPreferredWidth(60);
        table.getColumnModel().getColumn(5).setPreferredWidth(80);
        table.getColumnModel().getColumn(6).setPreferredWidth(100);
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void loadTranscript() {
        students.getMyTranscript().thenAccept(response -> SwingUtilities.invokeLater(() -> {
            if (response.success() && response.data() != null) {
                StudentTranscriptView transcript = response.data();
                summaryLabel.setText(transcript.studentName() + " (" + transcript.studentNumber()
                        + ") — " + transcript.majorName() + " " + transcript.enrollmentYear() + "级");
                gradeModel.setGrades(transcript.grades());
                String electiveStatus = transcript.electivePassed() >= transcript.minElectiveCount()
                        ? "✓" : "✗";
                statusLabel.setText("必修通过: " + transcript.requiredPassed() + "/" + transcript.requiredTotal()
                        + " | 选修通过: " + transcript.electivePassed() + "/" + transcript.electiveTotal()
                        + " (要求 ≥" + transcript.minElectiveCount() + "门 " + electiveStatus + ")"
                        + " | 已获学分: " + transcript.creditsEarned());
            } else {
                statusLabel.setText("暂无成绩单");
            }
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> statusLabel.setText("加载失败: " + ex.getMessage()));
            return null;
        });
    }

    private static class GradeTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"课程代码", "课程名称", "学分", "类型", "学期", "修读学期", "结果"};
        private List<StudentGradeView> grades = List.of();

        void setGrades(List<StudentGradeView> grades) {
            this.grades = List.copyOf(grades);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return grades.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override public Object getValueAt(int row, int col) {
            StudentGradeView g = grades.get(row);
            return switch (col) {
                case 0 -> g.courseCode();
                case 1 -> g.courseName();
                case 2 -> g.credits();
                case 3 -> g.courseType() == CourseType.REQUIRED ? "必修" : "选修";
                case 4 -> "第" + g.semester() + "学期";
                case 5 -> g.recordedSemester() != null ? g.recordedSemester() : "";
                case 6 -> g.result() == GradeResult.PASSED ? "PASSED" : "FAILED";
                default -> "";
            };
        }
    }
}
