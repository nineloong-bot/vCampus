package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.CourseType;

import javax.swing.*;
import java.awt.*;

/** Builds the workspace card forms used to edit courses and plan information. */
abstract class TrainingPlanManagementPanelWorkspaceView extends TrainingPlanManagementPanelSaving {

    protected TrainingPlanManagementPanelWorkspaceView(StudentClientService students) {
        super(students);
    }

    protected JPanel buildWorkspacePanel() {
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
}
