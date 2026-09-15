package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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

    private final CardLayout workspaceCardLayout = new CardLayout();
    private final JPanel workspaceCardPanel = new JPanel(workspaceCardLayout);
    private javax.swing.border.TitledBorder courseBorder;
    private javax.swing.border.TitledBorder planBorder;
    private JTextField courseCodeField, courseNameField, courseCreditsField;
    private JComboBox<CourseType> courseTypeBox;
    private JComboBox<String> courseSemesterBox;
    private JLabel courseMsgLabel;
    private TrainingPlanCourseView editingCourse;

    private JTextField planNameField, minCountField, minCreditsField;
    private JLabel planMsgLabel;
    private boolean isEditingPlan;
    private EmbeddedEditorHost editorHost;
    private boolean workspaceDirty;

    public TrainingPlanManagementPanel(StudentClientService students) {
        this.students = students;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buildUi();
        loadDepartments();
    }

    private void buildUi() {
        buildWorkspacePanel();
        trackWorkspaceChanges();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.38);
        splitPane.setLeftComponent(buildLeftPanel());
        splitPane.setRightComponent(buildCoursePanel());
        editorHost = new EmbeddedEditorHost(splitPane);
        add(editorHost, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel buildLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(4, 8));
        leftPanel.add(buildFilterPanel(), BorderLayout.NORTH);
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

    private JPanel buildWorkspacePanel() {
        workspaceCardPanel.setOpaque(false);

        // Card 1: Empty placeholder
        JPanel emptyPanel = new JPanel(new GridBagLayout());
        emptyPanel.setOpaque(false);
        emptyPanel.setBorder(BorderFactory.createTitledBorder("操作工作区"));
        JLabel hintLabel = new JLabel("<html><center style='color:#777777;'>工作区就绪<br><br>可点击上方【新建方案】<br>或右侧【添加课程】在此处直接编辑</center></html>", SwingConstants.CENTER);
        emptyPanel.add(hintLabel);
        workspaceCardPanel.add(emptyPanel, "EMPTY");

        // Card 2: Course form
        JPanel coursePanel = new JPanel(new BorderLayout(4, 4));
        courseBorder = BorderFactory.createTitledBorder("课程编辑");
        coursePanel.setBorder(courseBorder);

        JPanel courseFields = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 4, 3, 4);

        courseCodeField = new JTextField(12);
        courseNameField = new JTextField(12);
        courseCreditsField = new JTextField("2", 5);

        courseTypeBox = new JComboBox<>(CourseType.values());
        courseTypeBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == CourseType.REQUIRED) setText("必修");
                else if (value == CourseType.ELECTIVE) setText("选修");
                else if (value == CourseType.CROSS_DISCIPLINARY) setText("跨学科");
                return this;
            }
        });

        courseSemesterBox = new JComboBox<>();
        for (int i = 1; i <= 8; i++) courseSemesterBox.addItem("第" + i + "学期");

        courseMsgLabel = new JLabel(" ");
        courseMsgLabel.setForeground(new Color(220, 53, 69));

        int r = 0;
        c.gridx = 0; c.gridy = r; c.weightx = 0; courseFields.add(new JLabel("课程代码:"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1; courseFields.add(courseCodeField, c);

        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0; courseFields.add(new JLabel("课程名称:"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1; courseFields.add(courseNameField, c);

        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0; courseFields.add(new JLabel("学分:"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1; courseFields.add(courseCreditsField, c);

        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0; courseFields.add(new JLabel("类型:"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1; courseFields.add(courseTypeBox, c);

        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0; courseFields.add(new JLabel("学期:"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1; courseFields.add(courseSemesterBox, c);

        r++;
        c.gridx = 0; c.gridy = r; c.gridwidth = 2; courseFields.add(courseMsgLabel, c);

        JPanel courseBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        JButton cancelCourseBtn = new JButton("取消");
        cancelCourseBtn.addActionListener(e -> showEmptyWorkspace());
        JButton saveCourseBtn = new JButton("确定");
        saveCourseBtn.addActionListener(e -> saveCourse());
        courseBtnRow.add(cancelCourseBtn);
        courseBtnRow.add(saveCourseBtn);

        coursePanel.add(new JScrollPane(courseFields), BorderLayout.CENTER);
        coursePanel.add(courseBtnRow, BorderLayout.SOUTH);
        workspaceCardPanel.add(coursePanel, "COURSE");

        // Card 3: Plan form
        JPanel planPanel = new JPanel(new BorderLayout(4, 4));
        planBorder = BorderFactory.createTitledBorder("方案编辑");
        planPanel.setBorder(planBorder);

        JPanel planFields = new JPanel(new GridBagLayout());
        GridBagConstraints pc = new GridBagConstraints();
        pc.fill = GridBagConstraints.HORIZONTAL;
        pc.insets = new Insets(4, 4, 4, 4);

        planNameField = new JTextField(15);
        minCountField = new JTextField("4", 5);
        minCreditsField = new JTextField("8", 5);
        planMsgLabel = new JLabel(" ");
        planMsgLabel.setForeground(new Color(220, 53, 69));

        int pr = 0;
        pc.gridx = 0; pc.gridy = pr; pc.weightx = 0; planFields.add(new JLabel("方案名称:"), pc);
        pc.gridx = 1; pc.gridy = pr; pc.weightx = 1; planFields.add(planNameField, pc);

        pr++;
        pc.gridx = 0; pc.gridy = pr; pc.weightx = 0; planFields.add(new JLabel("最少选修门数:"), pc);
        pc.gridx = 1; pc.gridy = pr; pc.weightx = 1; planFields.add(minCountField, pc);

        pr++;
        pc.gridx = 0; pc.gridy = pr; pc.weightx = 0; planFields.add(new JLabel("最少选修学分:"), pc);
        pc.gridx = 1; pc.gridy = pr; pc.weightx = 1; planFields.add(minCreditsField, pc);

        pr++;
        pc.gridx = 0; pc.gridy = pr; pc.gridwidth = 2; planFields.add(planMsgLabel, pc);

        JPanel planBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        JButton cancelPlanBtn = new JButton("取消");
        cancelPlanBtn.addActionListener(e -> showEmptyWorkspace());
        JButton savePlanBtn = new JButton("确定");
        savePlanBtn.addActionListener(e -> savePlan());
        planBtnRow.add(cancelPlanBtn);
        planBtnRow.add(savePlanBtn);

        planPanel.add(new JScrollPane(planFields), BorderLayout.CENTER);
        planPanel.add(planBtnRow, BorderLayout.SOUTH);
        workspaceCardPanel.add(planPanel, "PLAN");

        workspaceCardLayout.show(workspaceCardPanel, "EMPTY");
        return workspaceCardPanel;
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
                long cross = currentPlan.courses().stream()
                        .filter(c -> c.courseType() == CourseType.CROSS_DISCIPLINARY).count();
                planInfoLabel.setText(currentPlan.majorName() + " " + currentPlan.enrollmentYear()
                        + "级 — " + currentPlan.planName()
                        + " | 必修" + required + "门 选修" + elective + "门 跨学科" + cross + "门"
                        + " | 选修毕业要求≥" + currentPlan.minElectiveCount() + "门"
                        + "≥" + currentPlan.minElectiveCredits() + "学分");
                statusLabel.setText("已加载方案");
            }
        }));
    }

    private void showEmptyWorkspace() {
        editingCourse = null;
        isEditingPlan = false;
        courseMsgLabel.setText(" ");
        planMsgLabel.setText(" ");
        workspaceCardLayout.show(workspaceCardPanel, "EMPTY");
        workspaceDirty = false;
        if (editorHost != null) editorHost.completeAndClose();
    }

    private void trackWorkspaceChanges() {
        DocumentListener listener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { workspaceDirty = true; }
            @Override public void removeUpdate(DocumentEvent event) { workspaceDirty = true; }
            @Override public void changedUpdate(DocumentEvent event) { workspaceDirty = true; }
        };
        for (JTextField field : List.of(planNameField, minCountField, minCreditsField,
                courseCodeField, courseNameField, courseCreditsField)) {
            field.getDocument().addDocumentListener(listener);
        }
        courseTypeBox.addActionListener(event -> workspaceDirty = true);
        courseSemesterBox.addActionListener(event -> workspaceDirty = true);
    }

    private void openCourseWorkspace(TrainingPlanCourseView existing) {
        if (currentPlan == null) {
            statusLabel.setText("请先查询并选择一个培养方案");
            return;
        }
        if (!editorHost.showEditor(new TrainingPlanCourseEditorPanel(
                workspaceCardPanel, () -> workspaceDirty))) return;
        editingCourse = existing;
        boolean isEdit = existing != null;
        courseBorder.setTitle(isEdit ? "编辑课程" : "添加课程");
        workspaceCardPanel.repaint();
        courseCodeField.setText(isEdit ? existing.courseCode() : "");
        courseNameField.setText(isEdit ? existing.courseName() : "");
        courseCreditsField.setText(isEdit ? existing.credits().toPlainString() : "2");
        courseTypeBox.setSelectedItem(isEdit ? existing.courseType() : CourseType.REQUIRED);
        courseSemesterBox.setSelectedIndex(isEdit ? Math.max(0, existing.semester() - 1) : 0);
        courseMsgLabel.setText(" ");
        workspaceCardLayout.show(workspaceCardPanel, "COURSE");
        workspaceDirty = false;
        courseCodeField.requestFocusInWindow();
    }

    private void editSelectedCourse() {
        if (currentPlan == null) {
            statusLabel.setText("请先查询并选择一个培养方案");
            return;
        }
        int row = courseTable.getSelectedRow();
        if (row < 0) {
            statusLabel.setText("请先在表格中选择一门课程");
            return;
        }
        openCourseWorkspace(courseModel.getCourse(row));
    }

    private void saveCourse() {
        if (currentPlan == null) {
            courseMsgLabel.setText("未选择有效方案");
            return;
        }
        String code = courseCodeField.getText().trim();
        String name = courseNameField.getText().trim();
        String creditsStr = courseCreditsField.getText().trim();
        if (code.isEmpty() || name.isEmpty()) {
            courseMsgLabel.setText("课程代码和名称不能为空");
            return;
        }
        BigDecimal credits;
        try {
            credits = new BigDecimal(creditsStr);
            if (credits.compareTo(BigDecimal.ZERO) <= 0) {
                courseMsgLabel.setText("学分必须大于0");
                return;
            }
        } catch (Exception ex) {
            courseMsgLabel.setText("学分格式无效");
            return;
        }
        CourseType type = (CourseType) courseTypeBox.getSelectedItem();
        int semester = courseSemesterBox.getSelectedIndex() + 1;
        SaveTrainingPlanCourseCommand cmd = new SaveTrainingPlanCourseCommand(
                currentPlan.planId(),
                editingCourse != null ? editingCourse.planCourseId() : null,
                code, name, credits, type, semester, true,
                editingCourse != null ? editingCourse.rowVersion() : 0,
                editingCourse != null ? editingCourse.courseId() : null,
                editingCourse != null ? editingCourse.offeringDepartmentId() : null,
                editingCourse != null ? editingCourse.offeringDepartmentName() : null,
                editingCourse != null ? editingCourse.allocatedQuota() : null);
        courseMsgLabel.setText("正在保存...");
        students.saveTrainingPlanCourse(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
            if (r.success()) {
                loadPlanDetail(currentPlan.planId());
                statusLabel.setText(editingCourse != null ? "课程修改成功" : "课程添加成功");
                showEmptyWorkspace();
            } else {
                courseMsgLabel.setText("保存失败: " + r.message());
                statusLabel.setText("保存失败: " + r.message());
            }
        }));
    }

    private void openNewPlanWorkspace() {
        MajorView major = (MajorView) majorBox.getSelectedItem();
        String yearStr = (String) yearBox.getSelectedItem();
        if (major == null || yearStr == null) {
            statusLabel.setText("请先选择院系、专业和年级");
            return;
        }
        if (!editorHost.showEditor(new TrainingPlanEditorPanel(
                workspaceCardPanel, () -> workspaceDirty))) return;
        int year = Integer.parseInt(yearStr.replace("级", ""));
        isEditingPlan = false;
        planBorder.setTitle("新建培养方案");
        workspaceCardPanel.repaint();
        planNameField.setText(major.name() + year + "级培养方案");
        minCountField.setText("4");
        minCreditsField.setText("8");
        planMsgLabel.setText(" ");
        workspaceCardLayout.show(workspaceCardPanel, "PLAN");
        workspaceDirty = false;
        planNameField.requestFocusInWindow();
    }

    private void openEditPlanWorkspace() {
        if (currentPlan == null) {
            statusLabel.setText("请先查询并选择一个方案");
            return;
        }
        if (!editorHost.showEditor(new TrainingPlanEditorPanel(
                workspaceCardPanel, () -> workspaceDirty))) return;
        isEditingPlan = true;
        planBorder.setTitle("编辑培养方案");
        workspaceCardPanel.repaint();
        planNameField.setText(currentPlan.planName());
        minCountField.setText(String.valueOf(currentPlan.minElectiveCount()));
        minCreditsField.setText(currentPlan.minElectiveCredits().toPlainString());
        planMsgLabel.setText(" ");
        workspaceCardLayout.show(workspaceCardPanel, "PLAN");
        workspaceDirty = false;
        planNameField.requestFocusInWindow();
    }

    private void savePlan() {
        MajorView major = (MajorView) majorBox.getSelectedItem();
        String yearStr = (String) yearBox.getSelectedItem();
        if (!isEditingPlan && (major == null || yearStr == null)) {
            planMsgLabel.setText("请先选择专业和年级");
            return;
        }
        String name = planNameField.getText().trim();
        String countStr = minCountField.getText().trim();
        String creditsStr = minCreditsField.getText().trim();
        if (name.isEmpty()) {
            planMsgLabel.setText("方案名称不能为空");
            return;
        }
        long minCount;
        BigDecimal minCredits;
        try {
            minCount = Long.parseLong(countStr);
            minCredits = new BigDecimal(creditsStr);
            if (minCount < 0 || minCredits.compareTo(BigDecimal.ZERO) < 0) {
                planMsgLabel.setText("门数与学分不能为负");
                return;
            }
        } catch (Exception ex) {
            planMsgLabel.setText("门数或学分数值无效");
            return;
        }

        if (isEditingPlan) {
            if (currentPlan == null) return;
            SaveTrainingPlanCommand cmd = new SaveTrainingPlanCommand(currentPlan.planId(),
                    currentPlan.majorId(), currentPlan.enrollmentYear(),
                    name, minCount, minCredits, true, currentPlan.rowVersion());
            planMsgLabel.setText("正在保存...");
            students.saveTrainingPlan(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                if (r.success()) {
                    loadPlanDetail(currentPlan.planId());
                    statusLabel.setText("方案更新成功");
                    showEmptyWorkspace();
                } else {
                    planMsgLabel.setText("更新失败: " + r.message());
                    statusLabel.setText("更新失败: " + r.message());
                }
            }));
        } else {
            int year = Integer.parseInt(yearStr.replace("级", ""));
            SaveTrainingPlanCommand cmd = new SaveTrainingPlanCommand(null, major.majorId(),
                    year, name, minCount, minCredits, true, 0);
            planMsgLabel.setText("正在创建...");
            students.saveTrainingPlan(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                if (r.success()) {
                    loadPlanDetail(r.data().planId());
                    statusLabel.setText("方案创建成功");
                    showEmptyWorkspace();
                } else {
                    planMsgLabel.setText("创建失败: " + r.message());
                    statusLabel.setText("创建失败: " + r.message());
                }
            }));
        }
    }

    private void createPlan() {
        openNewPlanWorkspace();
    }

    private void editPlan() {
        openEditPlanWorkspace();
    }

    private void showSaveCourseDialog(TrainingPlanCourseView existing) {
        openCourseWorkspace(existing);
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

    private void openCoursePoolDialog() {
        if (currentPlan == null) {
            statusLabel.setText("请先查询并选择一个培养方案");
            JOptionPane.showMessageDialog(this, "请先在左侧选择并查询一个培养方案，再从课程库引入课程。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DepartmentView currentDept = (DepartmentView) deptBox.getSelectedItem();
        String currentDeptId = currentDept != null ? currentDept.departmentId() : "";

        JPanel dialog = new JPanel(new BorderLayout(8, 8));

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

        CoursePoolTableModel poolModel = new CoursePoolTableModel();
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
        closeBtn.addActionListener(e -> editorHost.requestClose());
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
                showCrossCourseApplicationDialog(course);
            } else {
                showDirectAddCourseFromPoolDialog(course);
            }
        });

        editorHost.showEditor(new TrainingPlanCourseEditorPanel(dialog, () -> false));
    }

    private void showCrossCourseApplicationDialog(CoursePoolItemView course) {
        JPanel appDialog = new JPanel(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 8, 5, 8);

        int r = 0;
        c.gridx = 0; c.gridy = r; c.weightx = 0; form.add(new JLabel("目标培养方案:"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1; form.add(new JLabel(currentPlan.planName()), c);

        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0; form.add(new JLabel("申请课程:"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1;
        form.add(new JLabel(course.courseCode() + " " + course.courseName() + " (" + course.credits() + "学分)"), c);

        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0; form.add(new JLabel("开课学院:"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1; form.add(new JLabel(course.departmentName() != null ? course.departmentName() : "-"), c);

        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0; form.add(new JLabel("建议开课学期:"), c);
        JComboBox<String> semBox = new JComboBox<>();
        for (int i = 1; i <= 8; i++) semBox.addItem("第" + i + "学期");
        c.gridx = 1; c.gridy = r; c.weightx = 1; form.add(semBox, c);

        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0; form.add(new JLabel("申请选课名额:"), c);
        JTextField quotaField = new JTextField("30", 6);
        c.gridx = 1; c.gridy = r; c.weightx = 1; form.add(quotaField, c);

        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0; form.add(new JLabel("申请引入理由:"), c);
        JTextArea reasonArea = new JTextArea("申请作为跨学科选修课程引入", 3, 20);
        reasonArea.setLineWrap(true);
        c.gridx = 1; c.gridy = r; c.weightx = 1; form.add(new JScrollPane(reasonArea), c);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancelBtn = new JButton("取消");
        cancelBtn.addActionListener(e -> editorHost.requestClose());
        JButton submitBtn = new JButton("提交申请");
        btnRow.add(cancelBtn);
        btnRow.add(submitBtn);

        submitBtn.addActionListener(e -> {
            int quota;
            try {
                quota = Integer.parseInt(quotaField.getText().trim());
                if (quota <= 0) throw new NumberFormatException();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(appDialog, "申请名额必须是正整数", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int semester = semBox.getSelectedIndex() + 1;
            String reason = reasonArea.getText().trim();
            SubmitCrossCourseApplicationCommand cmd = new SubmitCrossCourseApplicationCommand(
                    course.courseId(), currentPlan.planId(), semester, quota, reason);
            students.submitCrossCourseApplication(cmd).thenAccept(res -> SwingUtilities.invokeLater(() -> {
                if (res.success()) {
                    JOptionPane.showMessageDialog(appDialog,
                            "跨学科课程引入申请已提交！\n待开课学院（" + course.departmentName() + "）管理员审批并分配名额后，课程将自动加入培养方案。",
                            "申请已提交", JOptionPane.INFORMATION_MESSAGE);
                    editorHost.completeAndClose();
                } else {
                    JOptionPane.showMessageDialog(appDialog, "提交申请失败: " + res.message(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }));
        });

        appDialog.add(form, BorderLayout.CENTER);
        appDialog.add(btnRow, BorderLayout.SOUTH);
        editorHost.showEditor(new CrossDisciplineRequestPanel(appDialog,
                () -> !reasonArea.getText().isBlank()));
    }

    private void showDirectAddCourseFromPoolDialog(CoursePoolItemView course) {
        JPanel addDialog = new JPanel(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 8, 5, 8);

        int r = 0;
        c.gridx = 0; c.gridy = r; c.weightx = 0; form.add(new JLabel("课程:"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1;
        form.add(new JLabel(course.courseCode() + " " + course.courseName() + " (" + course.credits() + "学分)"), c);

        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0; form.add(new JLabel("课程类别:"), c);
        JComboBox<CourseType> typeBox = new JComboBox<>(new CourseType[]{CourseType.REQUIRED, CourseType.ELECTIVE, CourseType.CROSS_DISCIPLINARY});
        typeBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == CourseType.REQUIRED) setText("必修");
                else if (value == CourseType.ELECTIVE) setText("选修");
                else if (value == CourseType.CROSS_DISCIPLINARY) setText("跨学科");
                return this;
            }
        });
        c.gridx = 1; c.gridy = r; c.weightx = 1; form.add(typeBox, c);

        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0; form.add(new JLabel("建议修读学期:"), c);
        JComboBox<String> semBox = new JComboBox<>();
        for (int i = 1; i <= 8; i++) semBox.addItem("第" + i + "学期");
        c.gridx = 1; c.gridy = r; c.weightx = 1; form.add(semBox, c);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancelBtn = new JButton("取消");
        cancelBtn.addActionListener(e -> editorHost.requestClose());
        JButton okBtn = new JButton("确定引入");
        btnRow.add(cancelBtn);
        btnRow.add(okBtn);

        okBtn.addActionListener(e -> {
            CourseType type = (CourseType) typeBox.getSelectedItem();
            int semester = semBox.getSelectedIndex() + 1;
            SaveTrainingPlanCourseCommand cmd = new SaveTrainingPlanCourseCommand(
                    currentPlan.planId(), null, course.courseCode(), course.courseName(),
                    course.credits(), type, semester, true, 0,
                    course.courseId(), course.departmentId(), course.departmentName(), null);
            students.saveTrainingPlanCourse(cmd).thenAccept(res -> SwingUtilities.invokeLater(() -> {
                if (res.success()) {
                    loadPlanDetail(currentPlan.planId());
                    statusLabel.setText("已成功引入课程: " + course.courseName());
                    JOptionPane.showMessageDialog(addDialog, "课程已成功引入培养方案！", "提示", JOptionPane.INFORMATION_MESSAGE);
                    editorHost.completeAndClose();
                } else {
                    JOptionPane.showMessageDialog(addDialog, "引入失败: " + res.message(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }));
        });

        addDialog.add(form, BorderLayout.CENTER);
        addDialog.add(btnRow, BorderLayout.SOUTH);
        editorHost.showEditor(new TrainingPlanCourseEditorPanel(addDialog, () -> false));
    }

    private void openCrossCourseReviewDialog() {
        JPanel reviewDialog = new JPanel(new BorderLayout(8, 8));

        JPanel topFilter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JComboBox<String> statusFilterBox = new JComboBox<>(new String[]{"待审批", "已同意", "已驳回", "全部"});
        JButton refreshBtn = new JButton("刷新");
        topFilter.add(new JLabel("审批状态:"));
        topFilter.add(statusFilterBox);
        topFilter.add(refreshBtn);
        reviewDialog.add(topFilter, BorderLayout.NORTH);

        CrossCourseApplicationTableModel appModel = new CrossCourseApplicationTableModel();
        JTable appTable = new JTable(appModel);
        appTable.setRowHeight(24);
        appTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        appTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (c instanceof JComponent jc && value != null) {
                    jc.setToolTipText(value.toString());
                }
                return c;
            }
        });
        reviewDialog.add(new JScrollPane(appTable), BorderLayout.CENTER);

        Runnable loadApps = () -> {
            String sel = (String) statusFilterBox.getSelectedItem();
            CrossCourseApplicationStatus st = switch (sel != null ? sel : "待审批") {
                case "待审批" -> CrossCourseApplicationStatus.PENDING;
                case "已同意" -> CrossCourseApplicationStatus.APPROVED;
                case "已驳回" -> CrossCourseApplicationStatus.REJECTED;
                default -> null;
            };
            students.listCrossCourseApplications(new CrossCourseApplicationQuery(null, null, st))
                    .thenAccept(r -> SwingUtilities.invokeLater(() -> {
                        if (r.success() && r.data() != null) {
                            appModel.setApplications(r.data());
                        } else {
                            JOptionPane.showMessageDialog(reviewDialog, "加载申请列表失败: " + r.message(), "错误", JOptionPane.ERROR_MESSAGE);
                        }
                    }));
        };
        refreshBtn.addActionListener(e -> loadApps.run());
        statusFilterBox.addActionListener(e -> loadApps.run());
        loadApps.run();

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton approveBtn = new JButton("同意并分配名额");
        JButton rejectBtn = new JButton("驳回申请");
        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> editorHost.requestClose());
        bottomBar.add(approveBtn);
        bottomBar.add(rejectBtn);
        bottomBar.add(closeBtn);
        reviewDialog.add(bottomBar, BorderLayout.SOUTH);

        approveBtn.addActionListener(e -> {
            int row = appTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(reviewDialog, "请先在表格中选择一项申请", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            CrossCourseApplicationView app = appModel.getApplication(row);
            if (app.status() != CrossCourseApplicationStatus.PENDING) {
                JOptionPane.showMessageDialog(reviewDialog, "该申请已审批，无法重复审批！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            editorHost.showEditor(new CrossCourseDecisionPanel(students, app, true, () -> {
                loadApps.run(); if (currentPlan != null) loadPlanDetail(currentPlan.planId());
            }, () -> editorHost.completeAndClose()));
        });

        rejectBtn.addActionListener(e -> {
            int row = appTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(reviewDialog, "请先在表格中选择一项申请", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            CrossCourseApplicationView app = appModel.getApplication(row);
            if (app.status() != CrossCourseApplicationStatus.PENDING) {
                JOptionPane.showMessageDialog(reviewDialog, "该申请已审批，无法重复审批！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            editorHost.showEditor(new CrossCourseDecisionPanel(students, app, false,
                    loadApps, () -> editorHost.completeAndClose()));
        });

        editorHost.showEditor(new CrossDisciplineRequestPanel(reviewDialog, () -> false));
    }

    private record DepartmentFilterItem(String id, String name) {
        @Override public String toString() { return name; }
    }

    private static class CoursePoolTableModel extends AbstractTableModel {
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

    private static class CrossCourseApplicationTableModel extends AbstractTableModel {
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

    private static class CourseTableModel extends AbstractTableModel {
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
}
