package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Admin panel for recording and viewing student grades. */
public final class GradeManagementPanel extends JPanel {
    private final StudentClientService students;
    private final JLabel statusLabel = new JLabel("输入学号查询学生成绩单");
    private final GradeTableModel gradeModel = new GradeTableModel();
    private final JTextField searchField = new JTextField(12);
    private String currentStudentId;

    public GradeManagementPanel(StudentClientService students) {
        this.students = students;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buildUi();
    }

    private void buildUi() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("学号:"));
        topPanel.add(searchField);
        JButton searchBtn = new JButton("查询");
        searchBtn.addActionListener(e -> searchStudent());
        topPanel.add(searchBtn);
        searchField.addActionListener(e -> searchStudent());

        JButton recordBtn = new JButton("录入成绩");
        recordBtn.addActionListener(e -> showRecordGradeDialog());
        topPanel.add(recordBtn);
        add(topPanel, BorderLayout.NORTH);

        JTable table = new JTable(gradeModel);
        table.setRowHeight(26);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(50);
        table.getColumnModel().getColumn(3).setPreferredWidth(50);
        table.getColumnModel().getColumn(4).setPreferredWidth(60);
        table.getColumnModel().getColumn(5).setPreferredWidth(80);
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void searchStudent() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) return;
        students.search(new StudentSearchQuery(keyword, null, null, null, null, 1, 1))
                .thenAccept(response -> SwingUtilities.invokeLater(() -> {
                    if (response.success() && response.data() != null
                            && !response.data().items().isEmpty()) {
                        StudentSummary student = response.data().items().get(0);
                        currentStudentId = student.studentId();
                        loadTranscript(student.studentId());
                    } else {
                        statusLabel.setText("未找到学生");
                        currentStudentId = null;
                        gradeModel.setGrades(List.of());
                    }
                }));
    }

    private void loadTranscript(String studentId) {
        students.getStudentTranscript(studentId).thenAccept(response ->
                SwingUtilities.invokeLater(() -> {
                    if (response.success() && response.data() != null) {
                        StudentTranscriptView transcript = response.data();
                        gradeModel.setGrades(transcript.grades());
                        statusLabel.setText(transcript.studentName() + " ("
                                + transcript.studentNumber() + ") — 必修 "
                                + transcript.requiredPassed() + "/" + transcript.requiredTotal()
                                + " | 选修 " + transcript.electivePassed() + "/"
                                + transcript.electiveTotal() + " | 学分 "
                                + transcript.creditsEarned());
                    }
                }));
    }

    private void showRecordGradeDialog() {
        if (currentStudentId == null) {
            statusLabel.setText("请先查询学生");
            return;
        }
        JTextField coursePlanIdField = new JTextField(20);
        JComboBox<GradeResult> resultBox = new JComboBox<>(GradeResult.values());
        JTextField semesterField = new JTextField("2024-2025-1", 12);

        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
        panel.add(new JLabel("方案课程ID:"));
        panel.add(coursePlanIdField);
        panel.add(new JLabel("结果:"));
        panel.add(resultBox);
        panel.add(new JLabel("修读学期:"));
        panel.add(semesterField);

        int result = JOptionPane.showConfirmDialog(this, panel, "录入成绩",
                JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            RecordStudentGradeCommand cmd = new RecordStudentGradeCommand(currentStudentId,
                    coursePlanIdField.getText().trim(),
                    (GradeResult) resultBox.getSelectedItem(),
                    semesterField.getText().trim());
            students.recordGrade(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                if (r.success()) {
                    loadTranscript(currentStudentId);
                    statusLabel.setText("成绩录入成功");
                } else {
                    statusLabel.setText("录入失败: " + r.message());
                }
            }));
        }
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
