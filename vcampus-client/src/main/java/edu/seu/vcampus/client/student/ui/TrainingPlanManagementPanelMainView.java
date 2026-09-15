package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/** Builds the main training plan layout: filter bar, workspace host and course table. */
abstract class TrainingPlanManagementPanelMainView extends TrainingPlanManagementPanelEditing {

    protected TrainingPlanManagementPanelMainView(StudentClientService students) {
        super(students);
    }

    protected void buildUi() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.38);
        splitPane.setLeftComponent(buildLeftPanel());
        splitPane.setRightComponent(buildCoursePanel());
        add(splitPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel buildLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(4, 8));
        leftPanel.add(buildFilterPanel(), BorderLayout.NORTH);
        leftPanel.add(workspaceCardPanel, BorderLayout.CENTER);
        return leftPanel;
    }

    private JPanel buildFilterPanel() {
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
        createBtn.addActionListener(e -> openNewPlanWorkspace());
        row4.add(createBtn);
        filterPanel.add(row4);

        filterPanel.add(Box.createVerticalStrut(8));
        planInfoLabel.setFont(planInfoLabel.getFont().deriveFont(Font.BOLD, 13f));
        planInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterPanel.add(planInfoLabel);

        deptBox.addActionListener(e -> onDepartmentChanged());
        majorBox.addActionListener(e -> onMajorChanged());

        return filterPanel;
    }

    private JPanel buildCoursePanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addCourseBtn = new JButton("添加课程");
        addCourseBtn.addActionListener(e -> openCourseWorkspace(null));
        JButton coursePoolBtn = new JButton("全校课程库引入");
        coursePoolBtn.addActionListener(e -> openCoursePoolDialog());
        JButton editCourseBtn = new JButton("编辑选中课程");
        editCourseBtn.addActionListener(e -> editSelectedCourse());
        JButton removeBtn = new JButton("删除选中课程");
        removeBtn.addActionListener(e -> removeSelectedCourse());
        JButton reviewAppsBtn = new JButton("跨学科审批");
        reviewAppsBtn.addActionListener(e -> openCrossCourseReviewDialog());
        JButton editPlanBtn = new JButton("编辑方案信息");
        editPlanBtn.addActionListener(e -> openEditPlanWorkspace());
        topBar.add(addCourseBtn);
        topBar.add(coursePoolBtn);
        topBar.add(editCourseBtn);
        topBar.add(removeBtn);
        topBar.add(reviewAppsBtn);
        topBar.add(editPlanBtn);
        panel.add(topBar, BorderLayout.NORTH);

        courseTable.setRowHeight(24);
        courseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        courseTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedCourse();
            }
        });
        courseTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (c instanceof JComponent jc && value != null) {
                    jc.setToolTipText(value.toString());
                }
                return c;
            }
        });
        courseTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        courseTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        courseTable.getColumnModel().getColumn(2).setPreferredWidth(40);
        courseTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        courseTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        courseTable.getColumnModel().getColumn(5).setPreferredWidth(60);
        courseTable.getColumnModel().getColumn(6).setPreferredWidth(50);
        panel.add(new JScrollPane(courseTable), BorderLayout.CENTER);
        return panel;
    }
}
