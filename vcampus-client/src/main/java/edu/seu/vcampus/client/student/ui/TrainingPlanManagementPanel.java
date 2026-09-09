package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Admin panel for managing training plans and their courses. */
public final class TrainingPlanManagementPanel extends JPanel {
    private final StudentClientService students;
    private final PlanSummaryTableModel planModel = new PlanSummaryTableModel();
    private final CourseTableModel courseModel = new CourseTableModel();
    private final JLabel statusLabel = new JLabel("就绪");
    private final JTable courseTable = new JTable(courseModel);
    private TrainingPlanDetailView currentPlan;

    public TrainingPlanManagementPanel(StudentClientService students) {
        this.students = students;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buildUi();
        loadPlans();
    }

    private void buildUi() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.4);
        splitPane.setLeftComponent(buildPlanListPanel());
        splitPane.setRightComponent(buildCoursePanel());
        add(splitPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel buildPlanListPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("新建方案");
        addBtn.addActionListener(e -> showSavePlanDialog(null));
        topBar.add(addBtn);
        panel.add(topBar, BorderLayout.NORTH);

        JTable table = new JTable(planModel);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) loadPlanDetail(planModel.getPlan(row).planId());
            }
        });
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCoursePanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addCourseBtn = new JButton("添加课程");
        addCourseBtn.addActionListener(e -> showSaveCourseDialog(null));
        JButton removeBtn = new JButton("删除选中课程");
        removeBtn.addActionListener(e -> removeSelectedCourse());
        topBar.add(addCourseBtn);
        topBar.add(removeBtn);
        panel.add(topBar, BorderLayout.NORTH);

        courseTable.setRowHeight(24);
        courseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        courseTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        courseTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        courseTable.getColumnModel().getColumn(2).setPreferredWidth(40);
        courseTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        courseTable.getColumnModel().getColumn(4).setPreferredWidth(50);
        panel.add(new JScrollPane(courseTable), BorderLayout.CENTER);
        return panel;
    }

    private void loadPlans() {
        students.searchTrainingPlans(new TrainingPlanQuery(null, null, 1, 100))
                .thenAccept(response -> SwingUtilities.invokeLater(() -> {
                    if (response.success() && response.data() != null) {
                        planModel.setPlans(response.data().items());
                        statusLabel.setText("共 " + response.data().total() + " 个培养方案");
                    }
                }));
    }

    private void loadPlanDetail(String planId) {
        students.getTrainingPlan(planId).thenAccept(response -> SwingUtilities.invokeLater(() -> {
            if (response.success() && response.data() != null) {
                currentPlan = response.data();
                courseModel.setCourses(currentPlan.courses());
            }
        }));
    }

    private void showSavePlanDialog(TrainingPlanSummary existing) {
        JTextField nameField = new JTextField(existing != null ? existing.planName() : "", 20);
        JTextField minCountField = new JTextField(existing != null
                ? String.valueOf(existing.minElectiveCount()) : "4", 5);
        JTextField minCreditsField = new JTextField(existing != null
                ? existing.minElectiveCredits().toPlainString() : "8", 5);

        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
        panel.add(new JLabel("方案名称:"));
        panel.add(nameField);
        panel.add(new JLabel("最少选修门数:"));
        panel.add(minCountField);
        panel.add(new JLabel("最少选修学分:"));
        panel.add(minCreditsField);

        if (existing == null) {
            JComboBox<DepartmentView> deptBox = new JComboBox<>();
            JComboBox<MajorView> majorBox = new JComboBox<>();
            JTextField yearField = new JTextField("2024", 5);
            panel.add(new JLabel("院系:"));
            panel.add(deptBox);
            panel.add(new JLabel("专业:"));
            panel.add(majorBox);
            panel.add(new JLabel("入学年份:"));
            panel.add(yearField);

            students.listDepartments(true).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                if (r.success() && r.data() != null) {
                    r.data().forEach(deptBox::addItem);
                    if (!r.data().isEmpty()) {
                        deptBox.setSelectedIndex(0);
                        loadMajors(deptBox, majorBox);
                    }
                }
            }));
            deptBox.addActionListener(e -> loadMajors(deptBox, majorBox));

            int result = JOptionPane.showConfirmDialog(this, panel, "新建培养方案",
                    JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION && majorBox.getSelectedItem() != null) {
                MajorView major = (MajorView) majorBox.getSelectedItem();
                SaveTrainingPlanCommand cmd = new SaveTrainingPlanCommand(null, major.majorId(),
                        Integer.parseInt(yearField.getText().trim()), nameField.getText().trim(),
                        Long.parseLong(minCountField.getText().trim()),
                        new BigDecimal(minCreditsField.getText().trim()), true, 0);
                students.saveTrainingPlan(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                    if (r.success()) {
                        loadPlans();
                        statusLabel.setText("方案创建成功");
                    } else {
                        statusLabel.setText("创建失败: " + r.message());
                    }
                }));
            }
        } else {
            int result = JOptionPane.showConfirmDialog(this, panel, "编辑培养方案",
                    JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                SaveTrainingPlanCommand cmd = new SaveTrainingPlanCommand(existing.planId(),
                        existing.majorId(), existing.enrollmentYear(), nameField.getText().trim(),
                        Long.parseLong(minCountField.getText().trim()),
                        new BigDecimal(minCreditsField.getText().trim()), true, existing.rowVersion());
                students.saveTrainingPlan(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                    if (r.success()) {
                        loadPlans();
                        statusLabel.setText("方案更新成功");
                    } else {
                        statusLabel.setText("更新失败: " + r.message());
                    }
                }));
            }
        }
    }

    private void showSaveCourseDialog(TrainingPlanCourseView existing) {
        if (currentPlan == null) {
            statusLabel.setText("请先选择一个培养方案");
            return;
        }
        JTextField codeField = new JTextField(existing != null ? existing.courseCode() : "", 10);
        JTextField nameField = new JTextField(existing != null ? existing.courseName() : "", 20);
        JTextField creditsField = new JTextField(existing != null
                ? existing.credits().toPlainString() : "2", 5);
        JComboBox<CourseType> typeBox = new JComboBox<>(CourseType.values());
        if (existing != null) typeBox.setSelectedItem(existing.courseType());
        JComboBox<String> semesterBox = new JComboBox<>();
        for (int i = 1; i <= 8; i++) semesterBox.addItem("第" + i + "学期");
        if (existing != null) semesterBox.setSelectedIndex(existing.semester() - 1);

        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
        panel.add(new JLabel("课程代码:"));
        panel.add(codeField);
        panel.add(new JLabel("课程名称:"));
        panel.add(nameField);
        panel.add(new JLabel("学分:"));
        panel.add(creditsField);
        panel.add(new JLabel("类型:"));
        panel.add(typeBox);
        panel.add(new JLabel("学期:"));
        panel.add(semesterBox);

        int result = JOptionPane.showConfirmDialog(this, panel,
                existing != null ? "编辑课程" : "添加课程", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            SaveTrainingPlanCourseCommand cmd = new SaveTrainingPlanCourseCommand(
                    currentPlan.planId(),
                    existing != null ? existing.planCourseId() : null,
                    codeField.getText().trim(), nameField.getText().trim(),
                    new BigDecimal(creditsField.getText().trim()),
                    (CourseType) typeBox.getSelectedItem(),
                    semesterBox.getSelectedIndex() + 1, true,
                    existing != null ? existing.rowVersion() : 0);
            students.saveTrainingPlanCourse(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                if (r.success()) {
                    loadPlanDetail(currentPlan.planId());
                    statusLabel.setText("课程保存成功");
                } else {
                    statusLabel.setText("保存失败: " + r.message());
                }
            }));
        }
    }

    private void removeSelectedCourse() {
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

    private void loadMajors(JComboBox<DepartmentView> deptBox, JComboBox<MajorView> majorBox) {
        DepartmentView dept = (DepartmentView) deptBox.getSelectedItem();
        if (dept == null) return;
        majorBox.removeAllItems();
        students.listMajors(dept.departmentId()).thenAccept(r -> SwingUtilities.invokeLater(() -> {
            if (r.success() && r.data() != null) r.data().forEach(majorBox::addItem);
        }));
    }

    private static class PlanSummaryTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"方案名称", "入学年份", "课程数", "状态"};
        private List<TrainingPlanSummary> plans = List.of();

        void setPlans(List<TrainingPlanSummary> plans) {
            this.plans = List.copyOf(plans);
            fireTableDataChanged();
        }

        TrainingPlanSummary getPlan(int row) { return plans.get(row); }
        @Override public int getRowCount() { return plans.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override public Object getValueAt(int row, int col) {
            TrainingPlanSummary p = plans.get(row);
            return switch (col) {
                case 0 -> p.departmentName() + " " + p.majorName();
                case 1 -> p.enrollmentYear() + "级";
                case 2 -> p.courseCount();
                case 3 -> p.isActive() ? "启用" : "停用";
                default -> "";
            };
        }
    }

    private static class CourseTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"课程代码", "课程名称", "学分", "类型", "学期"};
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
                case 3 -> c.courseType() == CourseType.REQUIRED ? "必修" : "选修";
                case 4 -> "第" + c.semester() + "学期";
                default -> "";
            };
        }
    }
}
