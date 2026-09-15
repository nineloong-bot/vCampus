package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.CrossCourseApplicationQuery;
import edu.seu.vcampus.common.student.CrossCourseApplicationStatus;
import edu.seu.vcampus.common.student.CrossCourseApplicationView;
import edu.seu.vcampus.common.student.ReviewCrossCourseApplicationCommand;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/** Modal dialog that approves or rejects cross-disciplinary course import applications. */
final class TrainingPlanCrossCourseReviewDialog {

    private TrainingPlanCrossCourseReviewDialog() {
    }

    static void show(Component owner, StudentClientService students, Runnable reloadPlan) {
        JDialog reviewDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(owner), "跨学科选课申请审批", true);
        reviewDialog.setSize(950, 520);
        reviewDialog.setLocationRelativeTo(owner);
        reviewDialog.setLayout(new BorderLayout(8, 8));

        JPanel topFilter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JComboBox<String> statusFilterBox = new JComboBox<>(new String[]{"待审批", "已同意", "已驳回", "全部"});
        JButton refreshBtn = new JButton("刷新");
        topFilter.add(new JLabel("审批状态:"));
        topFilter.add(statusFilterBox);
        topFilter.add(refreshBtn);
        reviewDialog.add(topFilter, BorderLayout.NORTH);

        TrainingPlanApplicationTableModel appModel = new TrainingPlanApplicationTableModel();
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
        closeBtn.addActionListener(e -> reviewDialog.dispose());
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
            String quotaStr = JOptionPane.showInputDialog(reviewDialog,
                    "申请学院: " + app.targetDepartmentName()
                    + "\n课程: " + app.courseName() + " (" + app.courseCode() + ")"
                    + "\n申请名额: " + app.requestedQuota()
                    + "\n\n请输入分配给该学院的选课名额:",
                    String.valueOf(app.requestedQuota()));
            if (quotaStr == null) return;
            int quota;
            try {
                quota = Integer.parseInt(quotaStr.trim());
                if (quota <= 0) throw new NumberFormatException();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(reviewDialog, "分配名额必须是大于0的正整数！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            ReviewCrossCourseApplicationCommand cmd = new ReviewCrossCourseApplicationCommand(
                    app.applicationId(), true, quota, null);
            students.reviewCrossCourseApplication(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                if (r.success()) {
                    JOptionPane.showMessageDialog(reviewDialog,
                            "审批成功！已分配名额 " + quota + "，该课程已自动加入目标培养方案。",
                            "审批成功", JOptionPane.INFORMATION_MESSAGE);
                    loadApps.run();
                    reloadPlan.run();
                } else {
                    JOptionPane.showMessageDialog(reviewDialog, "审批操作失败: " + r.message(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }));
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
            String reason = JOptionPane.showInputDialog(reviewDialog,
                    "申请学院: " + app.targetDepartmentName()
                    + "\n课程: " + app.courseName()
                    + "\n\n请输入驳回原因:",
                    "本学期选课容量已满");
            if (reason == null || reason.trim().isEmpty()) return;
            ReviewCrossCourseApplicationCommand cmd = new ReviewCrossCourseApplicationCommand(
                    app.applicationId(), false, null, reason.trim());
            students.reviewCrossCourseApplication(cmd).thenAccept(r -> SwingUtilities.invokeLater(() -> {
                if (r.success()) {
                    JOptionPane.showMessageDialog(reviewDialog, "已驳回该申请。", "提示", JOptionPane.INFORMATION_MESSAGE);
                    loadApps.run();
                } else {
                    JOptionPane.showMessageDialog(reviewDialog, "驳回操作失败: " + r.message(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }));
        });

        reviewDialog.setVisible(true);
    }
}
