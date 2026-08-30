package edu.seu.vcampus.client.library.ui;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import java.util.Objects;
import java.awt.*;
public final class LibraryPolicyPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private final JComboBox<String> role = new JComboBox<>(new String[]{"STUDENT", "TEACHER"});
    private final JSpinner maxLoans = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
    private final JSpinner loanDays = new JSpinner(new SpinnerNumberModel(30, 1, 365, 1));
    private final JSpinner renewals = new JSpinner(new SpinnerNumberModel(1, 0, 20, 1));
    private final JSpinner renewalDays = new JSpinner(new SpinnerNumberModel(15, 1, 365, 1));
    private final JSpinner version = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
    public LibraryPolicyPanel(LibraryClientService service) {
        super("library.policy", "借阅策略", "配置学生和教师的借阅与续借限制。", "角色", "最大在借", "借期", "最大续借", "续借天数", "操作");
        this.service = Objects.requireNonNull(service, "service");
        JButton save = new JButton("保存策略");
        save.addActionListener(event -> save(new UpdateLibraryPolicyCommand((String) role.getSelectedItem(),
                (Integer) maxLoans.getValue(), (Integer) loanDays.getValue(), (Integer) renewals.getValue(),
                (Integer) renewalDays.getValue(), ((Integer) version.getValue()).longValue())));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8)); form.setOpaque(false);
        form.add(new JLabel("角色")); form.add(role); form.add(new JLabel("最大在借")); form.add(maxLoans);
        form.add(new JLabel("借期")); form.add(loanDays); form.add(new JLabel("最大续借")); form.add(renewals);
        form.add(new JLabel("续借天数")); form.add(renewalDays); form.add(new JLabel("当前版本")); form.add(version);
        form.add(save); add(form, BorderLayout.SOUTH);
    }
    public void save(UpdateLibraryPolicyCommand command) {
        long request = beginRequest();
        status.setText("正在保存借阅策略……");
        service.updatePolicy(command).whenComplete((policy, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    if (failure != null) { status.setText("借阅策略保存失败，请刷新后重试"); return; }
                    role.setSelectedItem(policy.roleCode()); maxLoans.setValue(policy.maxActiveLoans());
                    loanDays.setValue(policy.loanDays()); renewals.setValue(policy.maxRenewals());
                    renewalDays.setValue(policy.renewalDays()); version.setValue((int) policy.rowVersion());
                    status.setText(("STUDENT".equals(policy.roleCode()) ? "学生" : "教师") + "借阅策略已保存");
                }));
    }
}
