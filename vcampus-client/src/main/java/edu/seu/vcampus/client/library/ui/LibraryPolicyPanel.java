package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/** Fixed library settings form: one row per borrower type plus read-only runtime status. */
public final class LibraryPolicyPanel extends JPanel {
    private final LibraryClientService service;
    private final PolicyRow student = new PolicyRow("STUDENT", "学生", 5, 30, 1, 15);
    private final PolicyRow teacher = new PolicyRow("TEACHER", "教师", 10, 60, 2, 30);
    private final JLabel message = new JLabel("可分别调整学生和教师的借阅规则");
    private final JLabel serverStatus = new JLabel("检查中");
    private final JLabel databaseStatus = new JLabel("检查中");
    private long refreshSequence;

    public LibraryPolicyPanel(LibraryClientService service) {
        super(new BorderLayout(0, 14));
        this.service = Objects.requireNonNull(service, "service");
        setName("library.policy");
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        setBackground(LibraryPalette.PAGE);

        JPanel header = new JPanel(new GridLayout(0, 1, 0, 4));
        header.setOpaque(false);
        JLabel title = new JLabel("图书管理设置"); title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        header.add(title); header.add(new JLabel("固定展示两类身份的全部借阅配置，保存时互不影响。"));
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(); content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(columnHeader()); content.add(student.panel()); content.add(Box.createVerticalStrut(8));
        content.add(teacher.panel()); content.add(Box.createVerticalStrut(18)); content.add(statusPanel());
        add(content, BorderLayout.CENTER);
        add(message, BorderLayout.SOUTH);
        LibraryUiStyle.apply(this);
    }

    public void refreshStatus() {
        long request = ++refreshSequence;
        student.setSaveEnabled(false); teacher.setSaveEnabled(false);
        serverStatus.setText("检查中"); databaseStatus.setText("检查中");
        message.setText("正在读取服务端设置……");
        service.searchBooks(new BookSearchQuery("", null, false, 1, 1))
                .whenComplete((page, failure) -> SwingUtilities.invokeLater(() -> {
                    if (request != refreshSequence) return;
                    if (failure == null) { serverStatus.setText("已连接"); databaseStatus.setText("可访问"); }
                    else { serverStatus.setText("连接异常"); databaseStatus.setText("无法确认"); }
                }));
        service.getPolicies().whenComplete((policies, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (request != refreshSequence) return;
                    if (failure != null) {
                        LibraryFeedback.failure(this, message, failure,
                                "设置读取失败，请检查服务端连接后重试。");
                        return;
                    }
                    for (LibraryPolicyView policy : policies) {
                        if ("STUDENT".equals(policy.roleCode())) student.apply(policy);
                        if ("TEACHER".equals(policy.roleCode())) teacher.apply(policy);
                    }
                    message.setText("已读取最新借阅设置");
                }));
    }

    public void save(UpdateLibraryPolicyCommand command) {
        long request = ++refreshSequence;
        student.setSaveEnabled(false); teacher.setSaveEnabled(false);
        message.setText("正在保存设置……");
        service.updatePolicy(command).whenComplete((policy, failure) -> SwingUtilities.invokeLater(() -> {
            if (request != refreshSequence) return;
            if (failure != null) {
                student.setSaveEnabled(true); teacher.setSaveEnabled(true);
                LibraryFeedback.failure(this, message, failure, "设置保存失败，请刷新后重试。");
                return;
            }
            PolicyRow row = "STUDENT".equals(policy.roleCode()) ? student : teacher;
            row.apply(policy);
            student.setSaveEnabled(true); teacher.setSaveEnabled(true);
            message.setText(("STUDENT".equals(policy.roleCode()) ? "学生" : "教师") + "借阅策略已保存");
        }));
    }

    private JPanel columnHeader() {
        JPanel row = rowPanel();
        for (String text : new String[]{"适用身份", "最大同时借阅", "借阅期限（天）", "最大续借次数", "续借期限（天）", ""})
            row.add(new JLabel(text));
        return row;
    }

    private JPanel statusPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 12, 8));
        panel.setBorder(BorderFactory.createTitledBorder("运行状态（只读）"));
        panel.setBackground(LibraryPalette.SURFACE);
        panel.add(new JLabel("服务端状态")); panel.add(serverStatus);
        panel.add(new JLabel("数据库状态")); panel.add(databaseStatus);
        panel.add(new JLabel("配置来源")); panel.add(new JLabel("服务端数据库 tblLibraryPolicy"));
        return panel;
    }

    private static JPanel rowPanel() {
        JPanel row = new JPanel(new GridLayout(1, 6, 10, 6)); row.setOpaque(false); return row;
    }

    private final class PolicyRow {
        private final String roleCode;
        private final String label;
        private final JSpinner maxLoans, loanDays, renewals, renewalDays;
        private JButton saveButton;
        private long version;

        PolicyRow(String roleCode, String label, int max, int days, int renew, int renewal) {
            this.roleCode = roleCode; this.label = label;
            maxLoans = spinner(max, 1, 100); loanDays = spinner(days, 1, 365);
            renewals = spinner(renew, 0, 20); renewalDays = spinner(renewal, 1, 365);
        }

        JPanel panel() {
            JPanel row = rowPanel(); row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(LibraryPalette.BORDER), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
            row.add(new JLabel(label)); row.add(maxLoans); row.add(loanDays); row.add(renewals); row.add(renewalDays);
            saveButton = new JButton("保存" + label + "设置");
            saveButton.setEnabled(false);
            saveButton.addActionListener(event -> save(new UpdateLibraryPolicyCommand(roleCode,
                    value(maxLoans), value(loanDays), value(renewals), value(renewalDays), version)));
            row.add(saveButton); return row;
        }

        void apply(LibraryPolicyView policy) {
            maxLoans.setValue(policy.maxActiveLoans()); loanDays.setValue(policy.loanDays());
            renewals.setValue(policy.maxRenewals()); renewalDays.setValue(policy.renewalDays()); version = policy.rowVersion();
            setSaveEnabled(true);
        }

        void setSaveEnabled(boolean enabled) { if (saveButton != null) saveButton.setEnabled(enabled); }
    }

    private static JSpinner spinner(int value, int min, int max) { return new JSpinner(new SpinnerNumberModel(value, min, max, 1)); }
    private static int value(JSpinner spinner) { return (Integer) spinner.getValue(); }
}
