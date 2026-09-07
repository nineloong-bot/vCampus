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
    private boolean saving;
    private Runnable afterMutation = () -> { };

    void setAfterMutation(Runnable refresh) { afterMutation = Objects.requireNonNull(refresh); }

    public LibraryPolicyPanel(LibraryClientService service) {
        super(new BorderLayout(0, 14));
        this.service = Objects.requireNonNull(service, "service");
        setName("library.policy");
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        setBackground(LibraryPalette.PAGE);

        JPanel header = new JPanel(new GridLayout(0, 1, 0, 4));
        header.setOpaque(false);
        JLabel title = new JLabel("借阅策略设置"); title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        header.add(title); header.add(new JLabel("按身份设置借阅期限和罚金。金额单位：元；设置为 0 表示该项不罚款。"));
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(); content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(student.panel()); content.add(Box.createVerticalStrut(8));
        content.add(teacher.panel()); content.add(Box.createVerticalStrut(18)); content.add(statusPanel());
        JScrollPane scroll = new JScrollPane(content); scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(20); add(scroll, BorderLayout.CENTER);
        add(message, BorderLayout.SOUTH);
        LibraryUiStyle.apply(this);
    }

    public void refreshStatus() {
        refreshStatus(false);
    }

    public void refreshAfterMutation() {
        refreshStatus(true);
    }

    private void refreshStatus(boolean preserveEdits) {
        if (saving) return;
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
                        if ("STUDENT".equals(policy.roleCode())) student.apply(policy, preserveEdits);
                        if ("TEACHER".equals(policy.roleCode())) teacher.apply(policy, preserveEdits);
                    }
                    message.setText("已读取最新借阅设置");
                }));
    }

    public void save(UpdateLibraryPolicyCommand command) {
        if (saving) return;
        saving = true;
        long request = ++refreshSequence;
        student.setSaveEnabled(false); teacher.setSaveEnabled(false);
        message.setText("正在保存设置……");
        service.updatePolicy(command).whenComplete((policy, failure) -> SwingUtilities.invokeLater(() -> {
            saving = false;
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
            afterMutation.run();
        }));
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

    private final class PolicyRow {
        private final String roleCode;
        private final String label;
        private final JSpinner maxLoans, loanDays, renewals, renewalDays;
        private final JSpinner firstTier = spinner(7, 1, 3649), secondTier = spinner(30, 2, 3650);
        private final JSpinner firstRate = moneySpinner(0.5), secondRate = moneySpinner(1), thirdRate = moneySpinner(2);
        private final JSpinner minorFine = moneySpinner(10), majorFine = moneySpinner(50), lostFine = moneySpinner(100);
        private JButton saveButton;
        private long version;
        private boolean dirty, applying;

        PolicyRow(String roleCode, String label, int max, int days, int renew, int renewal) {
            this.roleCode = roleCode; this.label = label;
            maxLoans = spinner(max, 1, 100); loanDays = spinner(days, 1, 365);
            renewals = spinner(renew, 0, 20); renewalDays = spinner(renewal, 1, 365);
            for (JSpinner field : fields())
                field.addChangeListener(event -> { if (!applying) dirty = true; });
        }

        private JSpinner[] fields() {
            return new JSpinner[]{maxLoans, loanDays, renewals, renewalDays, firstTier, secondTier,
                    firstRate, secondRate, thirdRate, minorFine, majorFine, lostFine};
        }

        JPanel panel() {
            JPanel row = new JPanel(new BorderLayout(0, 8)) {
                @Override public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
                }
            };
            row.setBackground(LibraryPalette.SURFACE);
            row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(label + "借阅策略"),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));
            JPanel form = new JPanel(new GridLayout(0, 4, 12, 8)); form.setOpaque(false);
            String[] labels = {"最大同时借阅", "借阅期限（天）", "最大续借次数", "续借期限（天）",
                    "第一档截至逾期天数", "第二档截至逾期天数", "第一档日罚金（元）", "第二档日罚金（元）",
                    "第三档日罚金（元）", "轻度损坏赔偿（元）", "严重损坏赔偿（元）", "遗失赔偿（元）"};
            JSpinner[] fields = fields();
            for (int i = 0; i < fields.length; i++) {
                fields[i].setName("library.policy." + roleCode + "." + i);
                fields[i].getAccessibleContext().setAccessibleName(labels[i]);
                form.add(new JLabel(labels[i])); form.add(fields[i]);
            }
            row.add(form, BorderLayout.CENTER);
            JPanel footer = new JPanel(new BorderLayout()); footer.setOpaque(false);
            footer.add(new JLabel("<html>逾期不足一天按一天；各档天数累加计费，超出第二档按第三档。<br>逾期罚金与损坏／遗失赔偿相加，归还或遗失登记时按当前策略结算。</html>"));
            saveButton = new JButton("保存" + label + "设置"); saveButton.setEnabled(false);
            saveButton.addActionListener(event -> {
                try {
                    for (JSpinner field : fields()) field.commitEdit();
                    save(new UpdateLibraryPolicyCommand(roleCode, value(maxLoans), value(loanDays),
                            value(renewals), value(renewalDays), version,
                            new PenaltyPolicy(value(firstTier), value(secondTier), money(firstRate), money(secondRate),
                                    money(thirdRate), money(minorFine), money(majorFine), money(lostFine))));
                } catch (java.text.ParseException | IllegalArgumentException | ArithmeticException failure) {
                    message.setText("请输入有效数值：逾期分档须递增，金额为非负数且最多两位小数。");
                }
            });
            footer.add(saveButton, BorderLayout.EAST); row.add(footer, BorderLayout.SOUTH);
            return row;
        }

        void apply(LibraryPolicyView policy) {
            apply(policy, false);
        }

        void apply(LibraryPolicyView policy, boolean preserveEdits) {
            if (preserveEdits && dirty) { setSaveEnabled(true); return; }
            applying = true;
            maxLoans.setValue(policy.maxActiveLoans()); loanDays.setValue(policy.loanDays());
            renewals.setValue(policy.maxRenewals()); renewalDays.setValue(policy.renewalDays()); version = policy.rowVersion();
            PenaltyPolicy penalty = policy.penalties();
            firstTier.setValue(penalty.firstTierDays()); secondTier.setValue(penalty.secondTierDays());
            firstRate.setValue(penalty.firstDailyFine().doubleValue()); secondRate.setValue(penalty.secondDailyFine().doubleValue());
            thirdRate.setValue(penalty.thirdDailyFine().doubleValue()); minorFine.setValue(penalty.minorDamageFine().doubleValue());
            majorFine.setValue(penalty.majorDamageFine().doubleValue()); lostFine.setValue(penalty.lostFine().doubleValue());
            applying = false; dirty = false;
            setSaveEnabled(true);
        }

        void setSaveEnabled(boolean enabled) { if (saveButton != null) saveButton.setEnabled(enabled); }
    }

    private static JSpinner moneySpinner(double value) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, 0.0, 1000000.0, 0.5));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.00")); return spinner;
    }
    private static java.math.BigDecimal money(JSpinner spinner) {
        return new java.math.BigDecimal(spinner.getValue().toString());
    }
    private static JSpinner spinner(int value, int min, int max) { return new JSpinner(new SpinnerNumberModel(value, min, max, 1)); }
    private static int value(JSpinner spinner) { return (Integer) spinner.getValue(); }
}
