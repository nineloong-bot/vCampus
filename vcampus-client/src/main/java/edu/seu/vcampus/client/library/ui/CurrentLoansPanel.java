package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Objects;
import java.util.List;

/** Current loans workspace panel with inline return condition selector and renewal. */
public final class CurrentLoansPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private List<LoanView> loans = List.of();

    public CurrentLoansPanel(LibraryClientService service) {
        super("library.current-loans", "当前借阅", "归还或续借本人当前记录。",
                "借阅号", "书名", "馆藏条码", "借出时间", "到期时间", "续借次数", "状态");
        this.service = Objects.requireNonNull(service, "service");
        JButton refresh = new JButton("刷新借阅");
        JButton renew = new JButton("续借所选");
        JComboBox<String> conditionCombo = new JComboBox<>(new String[]{"完好", "轻度损坏", "严重损坏"});
        JButton returned = new JButton("归还所选");
        refresh.addActionListener(event -> refresh());
        renew.addActionListener(event -> renewSelected());
        returned.addActionListener(event -> {
            if (selectedLoan() == null) return;
            int selected = conditionCombo.getSelectedIndex();
            returnSelected(ReturnCondition.values()[Math.max(0, selected)]);
        });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(refresh);
        actions.add(renew);
        actions.add(new JLabel("归还情况:"));
        actions.add(conditionCombo);
        actions.add(returned);
        add(actions, BorderLayout.SOUTH);
    }

    public void refresh() {
        long request = beginRequest();
        status.setText("正在加载当前借阅……");
        service.getCurrentLoans().whenComplete((loans, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    if (failure != null) { LibraryFeedback.failure(this, status, failure, "当前借阅加载失败，请重试。"); return; }
                    this.loans = List.copyOf(loans);
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.setRowCount(0);
                    for (LoanView loan : loans) model.addRow(new Object[]{loan.displayLoanNumber(),
                            LoanUiText.title(loan), LoanUiText.barcode(loan), loan.borrowedAt(),
                            loan.dueAt(), loan.renewCount(), LoanUiText.status(loan.status())});
                    status.setText(loans.isEmpty() ? "当前没有在借图书"
                            : "共 " + loans.size() + " 条当前借阅");
                }));
    }

    public void renewSelected() {
        LoanView loan = selectedLoan();
        if (loan == null) return;
        submit("正在续借……", "续借成功", service.renew(new RenewLoanCommand(loan.loanId(), loan.rowVersion())));
    }

    public void returnSelected() { returnSelected(ReturnCondition.NORMAL); }

    private void returnSelected(ReturnCondition condition) {
        LoanView loan = selectedLoan();
        if (loan == null) return;
        submit("正在归还……", "归还成功", service.returnBook(new ReturnBookCommand(loan.loanId(), loan.rowVersion(), condition)));
    }

    private LoanView selectedLoan() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= loans.size()) {
            status.setText("请先选择一条当前借阅记录");
            return null;
        }
        return loans.get(table.convertRowIndexToModel(row));
    }

    private void submit(String pending, String success, java.util.concurrent.CompletableFuture<LoanView> future) {
        long request = beginMutation();
        status.setText(pending);
        future.whenComplete((loan, failure) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsMutation(request)) return;
            if (failure == null) { status.setText(success + "，已登记罚金：¥" + loan.totalFine().setScale(2)); mutationSucceeded(); }
            else LibraryFeedback.failure(this, status, failure, "操作失败，请刷新后重试。");
        }));
    }
}
