package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.CoursePoolItemView;
import edu.seu.vcampus.common.student.CoursePoolQuery;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.TrainingPlanDetailView;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.function.Consumer;

/** Modal dialog that imports courses from the school-wide course pool into the current plan. */
final class TrainingPlanCoursePoolDialog {

    private TrainingPlanCoursePoolDialog() {
    }

    static void show(Component owner, StudentClientService students, TrainingPlanDetailView currentPlan,
                     DepartmentView selectedDept, Runnable reloadPlan, Consumer<String> statusWriter) {
        String currentDeptId = selectedDept != null ? selectedDept.departmentId() : "";

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(owner), "全校课程库引入", true);
        dialog.setSize(880, 520);
        dialog.setLocationRelativeTo(owner);
        dialog.setLayout(new BorderLayout(8, 8));

        JPanel topFilter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JComboBox<DepartmentFilterItem> poolDeptBox = new JComboBox<>();
        poolDeptBox.addItem(new DepartmentFilterItem(null, "全部开课学院"));
        JTextField kwField = new JTextField(12);
        JButton searchBtn = new JButton("搜索");
        topFilter.add(new JLabel("开课学院:"));
        topFilter.add(poolDeptBox);
        topFilter.add(new JLabel("课程关键字:"));
        topFilter.add(kwField);
        topFilter.add(searchBtn);
        dialog.add(topFilter, BorderLayout.NORTH);

        TrainingPlanPoolTableModel poolModel = new TrainingPlanPoolTableModel();
        JTable poolTable = new JTable(poolModel);
        poolTable.setRowHeight(24);
        poolTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        poolTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (c instanceof JComponent jc && value != null) {
                    jc.setToolTipText(value.toString());
                }
                return c;
            }
        });
        poolTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        poolTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        poolTable.getColumnModel().getColumn(2).setPreferredWidth(45);
        poolTable.getColumnModel().getColumn(3).setPreferredWidth(55);
        poolTable.getColumnModel().getColumn(4).setPreferredWidth(140);
        poolTable.getColumnModel().getColumn(5).setPreferredWidth(200);
        dialog.add(new JScrollPane(poolTable), BorderLayout.CENTER);

        students.listDepartments(true).thenAccept(r -> SwingUtilities.invokeLater(() -> {
            if (r.success() && r.data() != null) {
                for (DepartmentView d : r.data()) {
                    poolDeptBox.addItem(new DepartmentFilterItem(d.departmentId(), d.name()));
                }
            }
        }));

        Runnable doSearch = () -> {
            DepartmentFilterItem sel = (DepartmentFilterItem) poolDeptBox.getSelectedItem();
            String deptId = sel != null ? sel.id() : null;
            String kw = kwField.getText().trim();
            students.listCoursePool(new CoursePoolQuery(deptId, kw.isEmpty() ? null : kw))
                    .thenAccept(r -> SwingUtilities.invokeLater(() -> {
                        if (r.success() && r.data() != null) {
                            poolModel.setCourses(r.data());
                        } else {
                            JOptionPane.showMessageDialog(dialog, "获取课程库失败: " + r.message(), "错误", JOptionPane.ERROR_MESSAGE);
                        }
                    }));
        };
        searchBtn.addActionListener(e -> doSearch.run());
        doSearch.run();

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton importBtn = new JButton("引入到当前培养方案");
        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> dialog.dispose());
        bottomBar.add(importBtn);
        bottomBar.add(closeBtn);
        dialog.add(bottomBar, BorderLayout.SOUTH);

        importBtn.addActionListener(e -> {
            int row = poolTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(dialog, "请先在表格中选择一门课程", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            CoursePoolItemView course = poolModel.getCourse(row);
            boolean exists = currentPlan.courses().stream()
                    .anyMatch(c -> c.courseCode().equalsIgnoreCase(course.courseCode()));
            if (exists) {
                JOptionPane.showMessageDialog(dialog, "该课程已在当前培养方案中，无需重复引入！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean isCrossDept = course.departmentId() != null && !course.departmentId().equals(currentDeptId);
            if (isCrossDept) {
                TrainingPlanCourseImportDialogs.showCrossCourseApplication(dialog, students, currentPlan, course);
            } else {
                TrainingPlanCourseImportDialogs.showDirectAdd(dialog, students, currentPlan, course, reloadPlan, statusWriter);
            }
        });

        dialog.setVisible(true);
    }

    /** Filter item shown in the course pool department combo box. */
    record DepartmentFilterItem(String id, String name) {
        @Override public String toString() { return name; }
    }
}
