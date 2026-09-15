package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.CoursePoolItemView;
import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.common.student.SaveTrainingPlanCourseCommand;
import edu.seu.vcampus.common.student.SubmitCrossCourseApplicationCommand;
import edu.seu.vcampus.common.student.TrainingPlanDetailView;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Confirm dialogs for importing a pool course into the plan, directly or via application. */
final class TrainingPlanCourseImportDialogs {

    private TrainingPlanCourseImportDialogs() {
    }

    static void showCrossCourseApplication(JDialog parent, StudentClientService students,
                                           TrainingPlanDetailView currentPlan, CoursePoolItemView course) {
        JDialog appDialog = new JDialog(parent, "申请跨学科课程引入", true);
        appDialog.setSize(480, 360);
        appDialog.setLocationRelativeTo(parent);
        appDialog.setLayout(new BorderLayout(8, 8));

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
        cancelBtn.addActionListener(e -> appDialog.dispose());
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
                    appDialog.dispose();
                    parent.dispose();
                } else {
                    JOptionPane.showMessageDialog(appDialog, "提交申请失败: " + res.message(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }));
        });

        appDialog.add(form, BorderLayout.CENTER);
        appDialog.add(btnRow, BorderLayout.SOUTH);
        appDialog.setVisible(true);
    }

    static void showDirectAdd(JDialog parent, StudentClientService students,
                              TrainingPlanDetailView currentPlan, CoursePoolItemView course,
                              Runnable reloadPlan, Consumer<String> statusWriter) {
        JDialog addDialog = new JDialog(parent, "引入本院课程", true);
        addDialog.setSize(420, 260);
        addDialog.setLocationRelativeTo(parent);
        addDialog.setLayout(new BorderLayout(8, 8));

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
        cancelBtn.addActionListener(e -> addDialog.dispose());
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
                    reloadPlan.run();
                    statusWriter.accept("已成功引入课程: " + course.courseName());
                    JOptionPane.showMessageDialog(addDialog, "课程已成功引入培养方案！", "提示", JOptionPane.INFORMATION_MESSAGE);
                    addDialog.dispose();
                    parent.dispose();
                } else {
                    JOptionPane.showMessageDialog(addDialog, "引入失败: " + res.message(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }));
        });

        addDialog.add(form, BorderLayout.CENTER);
        addDialog.add(btnRow, BorderLayout.SOUTH);
        addDialog.setVisible(true);
    }
}
