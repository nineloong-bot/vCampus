package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
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
    private final CourseTableModel courseModel = new CourseTableModel();
    private final JLabel statusLabel = new JLabel("就绪");
    private final JTable courseTable = new JTable(courseModel);
    private final JComboBox<DepartmentView> deptBox = new JComboBox<>();
    private final JComboBox<MajorView> majorBox = new JComboBox<>();
    private final JComboBox<String> yearBox = new JComboBox<>();
    private final JLabel planInfoLabel = new JLabel("请选择院系、专业和年级");
    private TrainingPlanDetailView currentPlan;

    public TrainingPlanManagementPanel(StudentClientService students) {
        this.students = students;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buildUi();
        loadDepartments();
    }

    private void buildUi() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.35);
        splitPane.setLeftComponent(buildSelectorPanel());
        splitPane.setRightComponent(buildCoursePanel());
        add(splitPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel buildSelectorPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel filterPanel = new JPanel();
        filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
        filterPanel.setBorder(BorderFactory.createTitledBorder("选择培养方案"));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row1.add(new JLabel("院系:"));
        deptBox.setPreferredSize(new Dimension(160, 26));
        row1.add(deptBox);
        filterPanel.add(row1);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row2.add(new JLabel("专业:"));
        majorBox.setPreferredSize(new Dimension(160, 26));
        row2.add(majorBox);
        filterPanel.add(row2);

        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row3.add(new JLabel("年级:"));
        yearBox.setPreferredSize(new Dimension(160, 26));
        row3.add(yearBox);
        filterPanel.add(row3);

        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton queryBtn = new JButton("查询方案");
        queryBtn.addActionListener(e -> queryPlan());
        row4.add(queryBtn);
        JButton createBtn = new JButton("新建方案");
        createBtn.addActionListener(e -> createPlan());
        row4.add(createBtn);
        filterPanel.add(row4);

        filterPanel.add(Box.createVerticalStrut(8));
        planInfoLabel.setFont(planInfoLabel.getFont().deriveFont(Font.BOLD, 13f));
        planInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterPanel.add(planInfoLabel);

        deptBox.addActionListener(e -> onDepartmentChanged());
        majorBox.addActionListener(e -> onMajorChanged());

        panel.add(filterPanel, BorderLayout.NORTH);
        return panel;
    }

    private JPanel buildCoursePanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addCourseBtn = new JButton("添加课程");
        addCourseBtn.addActionListener(e -> showSaveCourseDialog(null));
        JButton removeBtn = new JButton("删除选中课程");
        removeBtn.addActionListener(e -> removeSelectedCourse());
        JButton editPlanBtn = new JButton("编辑方案信息");
        editPlanBtn.addActionListener(e -> editPlan());
        topBar.add(addCourseBtn);
        topBar.add(removeBtn);
        topBar.add(editPlanBtn);
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

    private void loadDepartments() {
        students.listDepartments(true).thenAccept(r -> SwingUtilities.invokeLater(() -> {
            if (r.success() && r.data() != null) {
                deptBox.removeAllItems();
                r.data().forEach(deptBox::addItem);
            }
        }));
    }

    private void onDepartmentChanged() {
        majorBox.removeAllItems();
        yearBox.removeAllItems();
        DepartmentView dept = (DepartmentView) deptBox.getSelectedItem();
        if (dept == null) return;
        students.listMajors(dept.departmentId()).thenAccept(r -> SwingUtilities.invokeLater(() -> {
            if (r.success() && r.data() != null) r.data().forEach(majorBox::addItem);
        }));
    }

    private void onMajorChanged() {
        yearBox.removeAllItems();
        MajorView major = (MajorView) majorBox.getSelectedItem();
        if (major == null) return;
        for (int y = 2020; y <= 2030; y++) yearBox.addItem(y + "级");
    }

    private void queryPlan() {
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

    private void loadPlanDetail(String planId) {
        students.getTrainingPlan(planId).thenAccept(response -> SwingUtilities.invokeLater(() -> {
            if (response.success() && response.data() != null) {
                currentPlan = response.data();
                courseModel.setCourses(currentPlan.courses());
                long required = currentPlan.courses().stream()
                        .filter(c -> c.courseType() == CourseType.REQUIRED).count();
                long elective = currentPlan.courses().stream()
                        .filter(c -> c.courseType() == CourseType.ELECTIVE).count();
                planInfoLabel.setText(currentPlan.majorName() + " " + currentPlan.enrollmentYear()
                        + "级 — " + currentPlan.planName()
                        + " | 必修" + required + "门 选修" + elective + "门"
                        + " | 选修毕业要求≥" + currentPlan.minElectiveCount() + "门"
                        + "≥" + currentPlan.minElectiveCredits() + "学分");
                statusLabel.setText("已加载方案");
            }
        }));
    }

    private void createPlan() {
        MajorView major = (MajorView) majorBox.getSelectedItem();
        String yearStr = (String) yearBox.getSelectedItem();
        if (major == null || yearStr == null) {
            statusLabel.setText("请先选择院系、专业和年级");
            return;
        }
        int year = Integer.parseInt(yearStr.replace("级", ""));
        JTextField nameField = new JTextField(major.name() + year + "级培养方案", 20);
        JTextField minCountField = new JTextField("4", 5);
        JTextField minCreditsField = new JTextField("8", 5);
        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
        panel.add(new JLabel("方案名称:")); panel.add(nameField);
        panel.add(new JLabel("最少选修门数:")); panel.add(minCountField);
        panel.add(new JLabel("最少选修学分:")); panel.add(minCreditsField);
        int result = JOptionPane.showConfirmDialog(this, panel, "新建培养方案",
                JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            SaveTrainingPlanCommand cmd = new SaveTrainingPlanCommand(null, major.majorId(),
                    year, nameField.getText().trim(),
                    Long.parseLong(minCountField.getText().trim()),
                    new BigDecimal(minCreditsField.getText().trim()), true, 0);
            students.saveTrainingPlan(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                if (r.success()) {
                    loadPlanDetail(r.data().planId());
                    statusLabel.setText("方案创建成功");
                } else {
                    statusLabel.setText("创建失败: " + r.message());
                }
            }));
        }
    }

    private void editPlan() {
        if (currentPlan == null) {
            statusLabel.setText("请先查询并选择一个方案");
            return;
        }
        JTextField nameField = new JTextField(currentPlan.planName(), 20);
        JTextField minCountField = new JTextField(String.valueOf(currentPlan.minElectiveCount()), 5);
        JTextField minCreditsField = new JTextField(currentPlan.minElectiveCredits().toPlainString(), 5);
        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
        panel.add(new JLabel("方案名称:")); panel.add(nameField);
        panel.add(new JLabel("最少选修门数:")); panel.add(minCountField);
        panel.add(new JLabel("最少选修学分:")); panel.add(minCreditsField);
        int result = JOptionPane.showConfirmDialog(this, panel, "编辑培养方案",
                JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            SaveTrainingPlanCommand cmd = new SaveTrainingPlanCommand(currentPlan.planId(),
                    currentPlan.majorId(), currentPlan.enrollmentYear(),
                    nameField.getText().trim(),
                    Long.parseLong(minCountField.getText().trim()),
                    new BigDecimal(minCreditsField.getText().trim()),
                    true, currentPlan.rowVersion());
            students.saveTrainingPlan(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                if (r.success()) {
                    loadPlanDetail(currentPlan.planId());
                    statusLabel.setText("方案更新成功");
                } else {
                    statusLabel.setText("更新失败: " + r.message());
                }
            }));
        }
    }

    private void showSaveCourseDialog(TrainingPlanCourseView existing) {
        if (currentPlan == null) {
            statusLabel.setText("请先查询并选择一个培养方案");
            return;
        }
        JTextField codeField = new JTextField(existing != null ? existing.courseCode() : "", 10);
        JTextField nameField = new JTextField(existing != null ? existing.courseName() : "", 20);
        JTextField creditsField = new JTextField(existing != null
                ? existing.credits().toPlainString() : "2", 5);
        JComboBox<CourseType> typeBox = new JComboBox<>(CourseType.values());
        typeBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == CourseType.REQUIRED) setText("必修");
                else if (value == CourseType.ELECTIVE) setText("选修");
                return this;
            }
        });
        if (existing != null) typeBox.setSelectedItem(existing.courseType());
        JComboBox<String> semesterBox = new JComboBox<>();
        for (int i = 1; i <= 8; i++) semesterBox.addItem("第" + i + "学期");
        if (existing != null) semesterBox.setSelectedIndex(existing.semester() - 1);

        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
        panel.add(new JLabel("课程代码:")); panel.add(codeField);
        panel.add(new JLabel("课程名称:")); panel.add(nameField);
        panel.add(new JLabel("学分:")); panel.add(creditsField);
        panel.add(new JLabel("类型:")); panel.add(typeBox);
        panel.add(new JLabel("学期:")); panel.add(semesterBox);

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
