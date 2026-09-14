package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.majortransfer.ExecuteMajorTransferCommand;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationView;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchView;
import edu.seu.vcampus.common.student.majortransfer.RecordMajorTransferScoreCommand;
import edu.seu.vcampus.common.student.majortransfer.SaveMajorTransferOptionCommand;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

final class MajorTransferCollegeDialogs {
    private MajorTransferCollegeDialogs() { }

    static void addOption(JComponent parent, StudentClientService students,
            MajorTransferBatchView batch, Runnable completed) {
        students.listDepartments(true).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response == null || !response.success() || response.data().isEmpty()) {
                        show(parent, "本学院信息加载失败");
                        return;
                    }
                    DepartmentView department = (DepartmentView) JOptionPane.showInputDialog(
                            parent, "选择学院", "招生专业", JOptionPane.PLAIN_MESSAGE,
                            null, response.data().toArray(), response.data().get(0));
                    if (department != null) chooseMajor(parent, students, batch,
                            department, completed);
                }));
    }

    private static void chooseMajor(JComponent parent, StudentClientService students,
            MajorTransferBatchView batch, DepartmentView department, Runnable completed) {
        students.listMajors(department.departmentId()).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response == null || !response.success() || response.data().isEmpty()) {
                        show(parent, "本学院专业加载失败");
                        return;
                    }
                    MajorView major = (MajorView) JOptionPane.showInputDialog(parent,
                            "选择专业", "招生专业", JOptionPane.PLAIN_MESSAGE,
                            null, response.data().toArray(), response.data().get(0));
                    if (major != null) optionForm(parent, students, batch, major, completed);
                }));
    }

    private static void optionForm(JComponent parent, StudentClientService students,
            MajorTransferBatchView batch, MajorView major, Runnable completed) {
        JTextField grades = new JTextField("2024,2025");
        JSpinner receive = new JSpinner(new SpinnerNumberModel(10, 0, 10000, 1));
        JSpinner interview = new JSpinner(new SpinnerNumberModel(20, 0, 10000, 1));
        JSpinner writtenWeight = new JSpinner(new SpinnerNumberModel(60, 0, 100, 1));
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        field(form, "专业", new JLabel(major.name()));
        field(form, "允许入学年份", grades);
        field(form, "接收名额", receive);
        field(form, "面试名额", interview);
        field(form, "笔试权重%", writtenWeight);
        if (JOptionPane.showConfirmDialog(parent, form, "招生专业配置",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        int weight = (Integer) writtenWeight.getValue();
        try {
            var command = new SaveMajorTransferOptionCommand(null, batch.batchId(),
                    major.majorId(), grades.getText().trim(), (Integer) receive.getValue(),
                    (Integer) interview.getValue(), 60.0, 60.0, weight, 100 - weight,
                    false, "", true, 0);
            students.saveTransferOption(command).whenComplete((response, failure) ->
                    SwingUtilities.invokeLater(() -> {
                        if (response != null && response.success()) completed.run();
                        else show(parent, message(response, "保存失败"));
                    }));
        } catch (RuntimeException error) {
            show(parent, error.getMessage());
        }
    }

    static void score(JComponent parent, StudentClientService students,
            MajorTransferApplicationView app, Runnable completed) {
        JTextField written = new JTextField(8);
        JTextField interview = new JTextField(8);
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        field(form, "笔试成绩", written);
        field(form, "面试成绩", interview);
        if (JOptionPane.showConfirmDialog(parent, form, "录入成绩",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            var command = new RecordMajorTransferScoreCommand(app.applicationId(),
                    decimal(written), decimal(interview), app.applicationVersion());
            students.recordTransferScore(command).whenComplete((response, failure) ->
                    SwingUtilities.invokeLater(() -> {
                        if (response != null && response.success()) completed.run();
                        else show(parent, message(response, "录入失败"));
                    }));
        } catch (NumberFormatException error) {
            show(parent, "成绩请输入数字");
        }
    }

    static void execute(JComponent parent, StudentClientService students,
            MajorTransferApplicationView app, Runnable completed) {
        students.listClasses(app.targetMajorId()).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response == null || !response.success() || response.data().isEmpty()) {
                        show(parent, "目标班级加载失败");
                        return;
                    }
                    var target = JOptionPane.showInputDialog(parent, "选择目标班级",
                            "执行转专业", JOptionPane.PLAIN_MESSAGE, null,
                            response.data().toArray(), response.data().get(0));
                    if (!(target instanceof edu.seu.vcampus.common.student.ClassView selected)) return;
                    students.executeTransfer(new ExecuteMajorTransferCommand(app.applicationId(),
                            selected.classId(), app.applicationVersion()))
                            .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
                                if (result != null && result.success()) completed.run();
                                else show(parent, message(result, "执行失败"));
                            }));
                }));
    }

    private static BigDecimal decimal(JTextField field) {
        return field.getText().isBlank() ? null : new BigDecimal(field.getText().trim());
    }

    private static void field(JPanel panel, String label, Component component) {
        panel.add(new JLabel(label));
        panel.add(component);
    }

    private static String message(edu.seu.vcampus.common.protocol.ResponseBody<?> response,
            String fallback) {
        return response == null || response.message() == null ? fallback : response.message();
    }

    private static void show(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message);
    }
}
